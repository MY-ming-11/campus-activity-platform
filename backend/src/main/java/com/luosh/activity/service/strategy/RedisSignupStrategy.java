package com.luosh.activity.service.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luosh.activity.common.BizException;
import com.luosh.activity.entity.Activity;
import com.luosh.activity.entity.Signup;
import com.luosh.activity.mapper.SignupMapper;
import com.luosh.activity.service.QuotaService;
import com.luosh.activity.service.SignupOutboxService;
import com.luosh.activity.vo.SignupResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 方案三：Redis 预扣 + 本地消息表异步落库。
 *
 * 这是项目原本的实现。名额的权威值在 Redis，数据库只承接真正成功的写入，
 * 失败请求（名额已满）根本不会碰到数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSignupStrategy implements SignupStrategy {

    private static final String STATUS_SIGNED = "SIGNED";
    private static final String STATUS_CANCELED = "CANCELED";

    private final QuotaService quotaService;
    private final SignupMapper signupMapper;
    private final SignupOutboxService outboxService;

    @Override
    public String key() {
        return "redis";
    }

    @Override
    public String label() {
        return "Redis 预扣 + 异步落库";
    }

    @Override
    public SignupResultVO signup(Activity activity, Long userId) {
        QuotaService.ReserveResult result =
                quotaService.reserve(activity.getId(), userId, activity.getQuota());

        if (result.isAlready()) {
            return new SignupResultVO(false, result.remaining(), "你已经报名过该活动了");
        }
        if (result.isSoldOut()) {
            throw new BizException("名额已经抢完了");
        }

        try {
            outboxService.enqueue(activity.getId(), userId);
        } catch (Exception e) {
            // 名额已经扣掉但消息没记下来，必须回滚名额，否则这个名额会永久消失
            quotaService.release(activity.getId(), userId);
            log.error("写消息表失败，已回滚名额 activityId={} userId={}", activity.getId(), userId, e);
            throw new BizException(500, "报名失败，请重试");
        }
        return new SignupResultVO(true, result.remaining(), "报名成功");
    }

    @Override
    public void cancel(Activity activity, Long userId) {
        Signup signup = signupMapper.selectOne(new LambdaQueryWrapper<Signup>()
                .eq(Signup::getActivityId, activity.getId())
                .eq(Signup::getUserId, userId));
        if (signup == null || !STATUS_SIGNED.equals(signup.getStatus())) {
            throw new BizException("你没有报名该活动，或已经取消过了");
        }

        // Redis 的 SREM 是幂等的，重复释放不会把名额加回去
        quotaService.release(activity.getId(), userId);

        Signup update = new Signup();
        update.setId(signup.getId());
        update.setStatus(STATUS_CANCELED);
        signupMapper.updateById(update);
    }

    @Override
    public void fillRemaining(List<Activity> activities) {
        quotaService.fillRemaining(activities);
    }
}
