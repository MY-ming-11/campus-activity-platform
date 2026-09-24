package com.luosh.activity.service;

import com.luosh.activity.common.BizException;
import com.luosh.activity.common.RedisKeys;
import com.luosh.activity.entity.Activity;
import com.luosh.activity.mapper.SignupMapper;
import com.luosh.activity.service.strategy.SignupStrategyResolver;
import com.luosh.activity.vo.MySignupVO;
import com.luosh.activity.vo.SignupResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignupService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final int RATE_CAPACITY = 5;
    private static final int RATE_REFILL_PER_SECOND = 1;

    private final ActivityService activityService;
    private final RateLimiter rateLimiter;
    private final SignupMapper signupMapper;
    private final SignupStrategyResolver strategyResolver;

    /**
     * 报名抢名额。前两步（限流、活动校验）三种策略共用，
     * 真正的并发控制在策略里——这样对比实验测出来的差异才归因于方案本身。
     *
     * @param overrideKey 对比实验用的策略覆盖，来自请求头；生产环境不生效
     */
    public SignupResultVO signup(Long activityId, Long userId, String overrideKey) {
        if (!rateLimiter.tryAcquire(RedisKeys.signupRate(userId), RATE_CAPACITY, RATE_REFILL_PER_SECOND)) {
            throw new BizException(429, "操作过于频繁，请稍后再试");
        }

        // 用 load 而不是 detail：detail 会顺带回填剩余名额，
        // 而悲观锁策略回填要 COUNT 一次报名表——那等于给这条路径凭空加一次查询，
        // 对比实验的数据就不干净了。
        Activity activity = activityService.load(activityId);
        checkSignable(activity);

        return strategyResolver.resolve(overrideKey).signup(activity, userId);
    }

    /**
     * 取消报名同样不加事务：Redis 释放本身幂等（SREM 返回 0 就不会回补余量），
     * 数据库那句 UPDATE 也是幂等的，两个人同时取消不会出问题。
     */
    public void cancel(Long activityId, Long userId, String overrideKey) {
        Activity activity = activityService.load(activityId);
        strategyResolver.resolve(overrideKey).cancel(activity, userId);
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
