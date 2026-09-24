package com.luosh.activity.service.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luosh.activity.common.BizException;
import com.luosh.activity.entity.Activity;
import com.luosh.activity.entity.Signup;
import com.luosh.activity.mapper.QuotaMapper;
import com.luosh.activity.mapper.SignupMapper;
import com.luosh.activity.vo.SignupResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 方案一：数据库悲观锁。
 *
 * 用 SELECT ... FOR UPDATE 把活动行锁住，同一活动的所有报名在这个锁上串行化。
 * 实现最简单、正确性最容易论证，代价是吞吐被串行化——同一活动的并发能力
 * 基本等于单个数据库连接的往返速度。
 */
@Component
@RequiredArgsConstructor
public class PessimisticSignupStrategy implements SignupStrategy {

    private static final String STATUS_SIGNED = "SIGNED";
    private static final String STATUS_CANCELED = "CANCELED";

    private final QuotaMapper quotaMapper;
    private final SignupMapper signupMapper;

    @Override
    public String key() {
        return "pessimistic";
    }

    @Override
    public String label() {
        return "数据库悲观锁（SELECT FOR UPDATE）";
    }

    /**
     * 注意 @Transactional 不能少：FOR UPDATE 加的锁会持有到事务提交，
     * 没有事务的话锁会立刻释放，等于没锁。
     */
    @Override
    @Transactional
    public SignupResultVO signup(Activity activity, Long userId) {
        Long activityId = activity.getId();

        // 第一句就锁住活动行，后面的统计和插入都在锁的保护范围内
        quotaMapper.lockActivity(activityId);

        Signup existing = findSignup(activityId, userId);
        long occupied = quotaMapper.countOccupiedBySignup(activityId);
        int quota = activity.getQuota();

        // 先判重复再判名额：已经报上名的人，活动满了也应该看到"你已经报名过"
        if (existing != null && !STATUS_CANCELED.equals(existing.getStatus())) {
            return new SignupResultVO(false, Math.max(0L, quota - occupied), "你已经报名过该活动了");
        }
        if (occupied >= quota) {
            throw new BizException("名额已经抢完了");
        }

        signupMapper.upsertSignup(activityId, userId);
        return new SignupResultVO(true, Math.max(0L, quota - occupied - 1), "报名成功");
    }

    /** 取消也要锁活动行，否则可能和报名交错，读到过期的占用数 */
    @Override
    @Transactional
    public void cancel(Activity activity, Long userId) {
        quotaMapper.lockActivity(activity.getId());

        Signup signup = findSignup(activity.getId(), userId);
        if (signup == null || !STATUS_SIGNED.equals(signup.getStatus())) {
            throw new BizException("你没有报名该活动，或已经取消过了");
        }

        Signup update = new Signup();
        update.setId(signup.getId());
        update.setStatus(STATUS_CANCELED);
        signupMapper.updateById(update);
    }

    @Override
    public void fillRemaining(List<Activity> activities) {
        QuotaSupport.fillByCount(quotaMapper, activities);
    }

    private Signup findSignup(Long activityId, Long userId) {
        return signupMapper.selectOne(new LambdaQueryWrapper<Signup>()
                .eq(Signup::getActivityId, activityId)
                .eq(Signup::getUserId, userId));
    }
}
