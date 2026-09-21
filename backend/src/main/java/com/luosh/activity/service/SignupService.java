package com.luosh.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luosh.activity.common.BizException;
import com.luosh.activity.common.RedisKeys;
import com.luosh.activity.entity.Activity;
import com.luosh.activity.entity.Signup;
import com.luosh.activity.mapper.SignupMapper;
import com.luosh.activity.vo.MySignupVO;
import com.luosh.activity.vo.SignupResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_SIGNED = "SIGNED";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final int RATE_CAPACITY = 5;
    private static final int RATE_REFILL_PER_SECOND = 1;

    private final ActivityService activityService;
    private final QuotaService quotaService;
    private final RateLimiter rateLimiter;
    private final SignupMapper signupMapper;
    private final SignupOutboxService outboxService;

    /**
     * 报名抢名额。整条链路的顺序不能换：
     * 限流 -> 校验活动 -> Redis 原子预扣 -> 记消息表 -> 异步落库。
     *
     * 这里刻意不加 @Transactional：整个方法只有一次 INSERT（写消息表），单条语句本身就是原子的，
     * 加事务反而会让每个请求一进来就占住一个数据库连接。连接池只有 20 个，
     * 压测时 1000 并发会全卡在等连接上，把 Redis 预扣省下来的时间全抵消掉。
     */
    public SignupResultVO signup(Long activityId, Long userId) {
        if (!rateLimiter.tryAcquire(RedisKeys.signupRate(userId), RATE_CAPACITY, RATE_REFILL_PER_SECOND)) {
            throw new BizException(429, "操作过于频繁，请稍后再试");
        }

        Activity activity = activityService.detail(activityId);
        checkSignable(activity);

        QuotaService.ReserveResult result = quotaService.reserve(activityId, userId, activity.getQuota());

        if (result.isAlready()) {
            // 幂等：同一个用户重复提交，不报错也不重复扣名额
            return new SignupResultVO(false, result.remaining(), "你已经报名过该活动了");
        }
        if (result.isSoldOut()) {
            throw new BizException("名额已经抢完了");
        }

        try {
            outboxService.enqueue(activityId, userId);
        } catch (Exception e) {
            // 名额已经扣掉了但消息没记下来，必须回滚名额，
            // 否则这个名额会永久消失，谁也抢不到。
            quotaService.release(activityId, userId);
            log.error("写消息表失败，已回滚名额 activityId={} userId={}", activityId, userId, e);
            throw new BizException(500, "报名失败，请重试");
        }

        return new SignupResultVO(true, result.remaining(), "报名成功");
    }

    /**
     * 取消报名同样不加事务：Redis 释放本身幂等（SREM 返回 0 就不会回补余量），
     * 数据库那句 UPDATE 也是幂等的，两个人同时取消不会出问题。
     */
    public void cancel(Long activityId, Long userId) {
        Signup signup = signupMapper.selectOne(new LambdaQueryWrapper<Signup>()
                .eq(Signup::getActivityId, activityId)
                .eq(Signup::getUserId, userId));

        if (signup == null || !STATUS_SIGNED.equals(signup.getStatus())) {
            throw new BizException("你没有报名该活动，或已经取消过了");
        }

        boolean released = quotaService.release(activityId, userId);
        if (!released) {
            // Redis 里没有这个人，说明两边的数据已经不一致了。
            // 这里只告警不阻断——用户的诉求是"取消"，不该因为这个失败。
            log.warn("取消报名时 Redis 未找到占位记录 activityId={} userId={}", activityId, userId);
        }

        Signup update = new Signup();
        update.setId(signup.getId());
        update.setStatus(STATUS_CANCELED);
        signupMapper.updateById(update);
    }

    public List<MySignupVO> mySignups(Long userId) {
        return signupMapper.selectMySignups(userId);
    }

    private void checkSignable(Activity activity) {
        if (!STATUS_PUBLISHED.equals(activity.getStatus())) {
            throw new BizException("该活动还没有开放报名");
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getSignupStart() != null && now.isBefore(activity.getSignupStart())) {
            throw new BizException("报名还没有开始");
        }
        if (activity.getSignupEnd() != null && now.isAfter(activity.getSignupEnd())) {
            throw new BizException("报名已经截止了");
        }
    }
}
