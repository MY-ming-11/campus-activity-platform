package com.luosh.activity.service.strategy;

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
 * 方案二：数据库乐观锁（条件更新 + 唯一索引）。
 *
 * 不用行锁，靠两句原子语句：
 *
 *   1. INSERT IGNORE INTO activity_signup ...  唯一索引保证同一用户只有一条
 *   2. UPDATE activity SET occupied = occupied + 1 WHERE id = ? AND occupied &lt; quota
 *      数据库对同一行的 UPDATE 本身就是串行的，条件放在 WHERE 里就不会超卖
 *
 * 【顺序为什么是先插入再扣减】
 * 如果反过来（先扣减再插入），同一用户并发双击时两次扣减都会成功，
 * 但唯一索引只留下一条报名记录，occupied 就虚高了——名额凭空少了。
 *
 * 先插入的话，第二次请求在唯一索引上拿不到插入（返回 0 行），
 * 自然就不会走到扣减那一步。而"扣减失败"的情况直接抛异常让事务回滚，
 * 刚插入的那条报名记录会一起被撤销，不需要手写补偿逻辑。
 */
@Component
@RequiredArgsConstructor
public class OptimisticSignupStrategy implements SignupStrategy {

    private static final String STATUS_CANCELED = "CANCELED";

    private final QuotaMapper quotaMapper;
    private final SignupMapper signupMapper;

    @Override
    public String key() {
        return "optimistic";
    }

    @Override
    public String label() {
        return "数据库乐观锁（条件更新）";
    }

    @Override
    @Transactional
    public SignupResultVO signup(Activity activity, Long userId) {
        Long activityId = activity.getId();

        int inserted = signupMapper.insertIgnore(activityId, userId);

        if (inserted == 0) {
            // 这人已经有一条报名记录了，拿加锁读看最新状态
            Signup existing = signupMapper.selectForUpdate(activityId, userId);
            if (existing != null && !STATUS_CANCELED.equals(existing.getStatus())) {
                return new SignupResultVO(false, remainingOf(activity), "你已经报名过该活动了");
            }
            // 取消过的人重新报名，同样要抢名额
            occupyOrThrow(activityId);
            signupMapper.markSigned(existing.getId());
            return new SignupResultVO(true, remainingOf(activity), "报名成功");
        }

        // 扣减失败直接抛异常，事务回滚会把上面那条插入一起撤掉
        occupyOrThrow(activityId);
        return new SignupResultVO(true, remainingOf(activity), "报名成功");
    }

    @Override
    @Transactional
    public void cancel(Activity activity, Long userId) {
        Signup signup = signupMapper.selectForUpdate(activity.getId(), userId);
        if (signup == null || STATUS_CANCELED.equals(signup.getStatus())) {
            throw new BizException("你没有报名该活动，或已经取消过了");
        }

        Signup update = new Signup();
        update.setId(signup.getId());
        update.setStatus(STATUS_CANCELED);
        signupMapper.updateById(update);

        quotaMapper.releaseOccupy(activity.getId());
    }

    /**
     * 这个方案回填剩余名额不用查数据库——
     * occupied 已经在 activity 行上，selectById 时一起带出来了。
     * 这是它相比悲观锁（要 COUNT 一遍报名表）的一个实际优势。
     */
    @Override
    public void fillRemaining(List<Activity> activities) {
        for (Activity activity : activities) {
            int occupied = activity.getOccupied() == null ? 0 : activity.getOccupied();
            activity.setRemaining(Math.max(0L, activity.getQuota() - occupied));
        }
    }

    private void occupyOrThrow(Long activityId) {
        if (quotaMapper.tryOccupy(activityId) == 0) {
            throw new BizException("名额已经抢完了");
        }
    }

    private long remainingOf(Activity activity) {
        Integer occupied = quotaMapper.selectOccupied(activity.getId());
        int used = occupied == null ? 0 : occupied;
        return Math.max(0L, activity.getQuota() - used);
    }
}
