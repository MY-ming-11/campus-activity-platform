package com.luosh.activity.service;

import com.luosh.activity.common.BizException;
import com.luosh.activity.common.RedisKeys;
import com.luosh.activity.entity.Activity;
import com.luosh.activity.entity.Checkin;
import com.luosh.activity.entity.Signup;
import com.luosh.activity.mapper.CheckinMapper;
import com.luosh.activity.mapper.SignupMapper;
import com.luosh.activity.vo.CheckinBoardVO;
import com.luosh.activity.vo.CheckinCodeVO;
import com.luosh.activity.vo.CheckinResultVO;
import com.luosh.activity.vo.CheckinRosterVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 签到。
 *
 * 核心是动态签到码：用活动自己的 checkin_secret，对「活动ID + 30 秒时间片」做 HMAC-SHA256，
 * 取摘要前 4 字节折算成 6 位数字。三个特性：
 *
 * 1. 每 30 秒自动换一个码，截图转发出去很快就失效
 * 2. 密钥只在服务端，前端拿不到就算不出下一个码
 * 3. 不同活动的码互不相通，因为活动 ID 参与了签名
 *
 * 校验时额外接受上一个时间片，不然学生刚看到码、走到输入框时刚好翻页就白输了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String STATUS_CHECKED = "CHECKED";

    /** 签到码只有 6 位，不能让人随便试，所以给得比报名更紧 */
    private static final int RATE_CAPACITY = 5;
    private static final int RATE_REFILL_PER_SECOND = 1;

    /** 活动开始前 1 小时开放签到，结束后 1 小时关闭，给组织方留出提前到场和收尾的余量 */
    private static final Duration OPEN_BEFORE = Duration.ofHours(1);
    private static final Duration CLOSE_AFTER = Duration.ofHours(1);

    private final ActivityService activityService;
    private final SignupMapper signupMapper;
    private final CheckinMapper checkinMapper;
    private final RateLimiter rateLimiter;
    private final CheckinCodeGenerator codeGenerator;

    /** 大屏展示用的当前签到码。 */
    public CheckinCodeVO currentCode(Long activityId) {
        Activity activity = activityService.detail(activityId);
        String code = codeGenerator.codeOf(
                activity.getId(), activity.getCheckinSecret(), codeGenerator.currentSlot());
        return new CheckinCodeVO(code, codeGenerator.secondsLeftInSlot());
    }

    /** 学生用签到码核销自己。 */
    public CheckinResultVO checkinByCode(Long activityId, Long userId, String code) {
        if (!rateLimiter.tryAcquire(RedisKeys.checkinRate(userId), RATE_CAPACITY, RATE_REFILL_PER_SECOND)) {
            throw new BizException(429, "尝试次数过多，请稍后再试");
        }

        Activity activity = activityService.detail(activityId);
        checkSignable(activity);
        checkWindow(activity);

        if (!codeGenerator.accepts(
                activity.getId(), activity.getCheckinSecret(), code, codeGenerator.currentSlot())) {
            throw new BizException("签到码不正确，或者已经过期了");
        }

        Signup signup = signupMapper.selectOne(new LambdaQueryWrapper<Signup>()
                .eq(Signup::getActivityId, activityId)
                .eq(Signup::getUserId, userId));

        return markChecked(activity, signup, userId, "QR", userId);
    }

    /**
     * 管理员手动核销，给没带手机或者手机没电的同学兜底。
     * 刻意不校验签到时间窗口——现场情况千变万化，组织方应该有权覆盖。
     */
    public CheckinResultVO manualCheckin(Long activityId, Long signupId, Long operatorId) {
        Activity activity = activityService.detail(activityId);
        Signup signup = signupMapper.selectById(signupId);
        if (signup == null || !activityId.equals(signup.getActivityId())) {
            throw new BizException("这条报名记录不存在");
        }
        return markChecked(activity, signup, signup.getUserId(), "MANUAL", operatorId);
    }

    /** 核销台的完整数据。 */
    public CheckinBoardVO board(Long activityId) {
        Activity activity = activityService.detail(activityId);
        List<CheckinRosterVO> records = checkinMapper.selectRoster(activityId);
        int checked = (int) records.stream().filter(r -> r.getCheckTime() != null).count();
        return new CheckinBoardVO(activity.getId(), activity.getTitle(), activity.getQuota(),
                activity.getRemaining(), records.size(), checked, records);
    }

    private CheckinResultVO markChecked(Activity activity, Signup signup, Long userId,
                                        String checkType, Long operatorId) {
        if (signup == null || STATUS_CANCELED.equals(signup.getStatus())) {
            throw new BizException("你没有报名这个活动");
        }
        if (STATUS_CHECKED.equals(signup.getStatus())) {
            return new CheckinResultVO(false, activity.getTitle(), "你已经签到过了");
        }

        Checkin checkin = new Checkin();
        checkin.setActivityId(activity.getId());
        checkin.setSignupId(signup.getId());
        checkin.setUserId(userId);
        checkin.setCheckType(checkType);
        checkin.setOperatorId(operatorId);
        try {
            checkinMapper.insert(checkin);
        } catch (DuplicateKeyException e) {
            // activity_checkin 上的 uk_signup 唯一索引兜底：
            // 两个请求同时核销同一张报名，只有一个能写进去，另一个落到这里。
            // 不靠"先查再插"，因为那之间有竞态。
            log.warn("重复核销 signupId={}", signup.getId());
            return new CheckinResultVO(false, activity.getTitle(), "你已经签到过了");
        }

        Signup update = new Signup();
        update.setId(signup.getId());
        update.setStatus(STATUS_CHECKED);
        signupMapper.updateById(update);

        return new CheckinResultVO(true, activity.getTitle(), "签到成功");
    }

    private void checkSignable(Activity activity) {
        if (!STATUS_PUBLISHED.equals(activity.getStatus())) {
            throw new BizException("这个活动还没有开放");
        }
    }

    private void checkWindow(Activity activity) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime().minus(OPEN_BEFORE))) {
            throw new BizException("签到还没开始，活动开始前 1 小时开放");
        }
        if (now.isAfter(activity.getEndTime().plus(CLOSE_AFTER))) {
            throw new BizException("这个活动的签到已经结束了");
        }
    }

}
