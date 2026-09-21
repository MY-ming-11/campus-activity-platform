package com.luosh.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luosh.activity.common.BizException;
import com.luosh.activity.common.PageVO;
import com.luosh.activity.dto.ActivityDTO;
import com.luosh.activity.entity.Activity;
import com.luosh.activity.mapper.ActivityMapper;
import com.luosh.activity.mapper.SignupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final String STATUS_DRAFT = "DRAFT";

    private final ActivityMapper activityMapper;
    private final SignupMapper signupMapper;
    private final QuotaService quotaService;

    public PageVO<Activity> page(long pageNo, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<Activity> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.like(Activity::getTitle, keyword);
        }
        if (StringUtils.hasText(status)) {
            query.eq(Activity::getStatus, status);
        }
        query.orderByDesc(Activity::getCreateTime);

        PageVO<Activity> result = PageVO.of(activityMapper.selectPage(new Page<>(pageNo, pageSize), query));
        quotaService.fillRemaining(result.records());
        return result;
    }

    public Activity detail(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BizException(404, "活动不存在");
        }
        quotaService.fillRemaining(List.of(activity));
        return activity;
    }

    @Transactional
    public Long create(ActivityDTO dto, Long creatorId) {
        validateTime(dto);

        Activity activity = new Activity();
        BeanUtils.copyProperties(dto, activity);
        activity.setId(null);
        activity.setCreatorId(creatorId);
        // 每个活动一份签名密钥，用于生成 30 秒轮换的动态签到码
        activity.setCheckinSecret(UUID.randomUUID().toString().replace("-", ""));
        activity.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : STATUS_DRAFT);
        activityMapper.insert(activity);
        return activity.getId();
    }

    @Transactional
    public void update(Long id, ActivityDTO dto) {
        Activity existing = detail(id);
        validateTime(dto);

        Activity activity = new Activity();
        BeanUtils.copyProperties(dto, activity);
        activity.setId(id);
        // 密钥不允许通过更新接口覆盖，置空后 MyBatis-Plus 会跳过这两个字段
        activity.setCheckinSecret(null);
        activity.setCreatorId(null);
        activityMapper.updateById(activity);

        // 名额上限被改过，Redis 里的余量必须跟着重算，
        // 否则改了配置 Redis 还是老值，两边永远对不上。
        if (!dto.getQuota().equals(existing.getQuota())) {
            long occupied = signupMapper.countOccupied(id);
            quotaService.syncStock(id, dto.getQuota(), occupied);
        }
    }

    @Transactional
    public void delete(Long id) {
        detail(id);
        activityMapper.deleteById(id);
    }

    private void validateTime(ActivityDTO dto) {
        if (dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new BizException("活动结束时间不能早于开始时间");
        }
        if (dto.getSignupStart().isAfter(dto.getSignupEnd())) {
            throw new BizException("报名开始时间不能晚于报名截止时间");
        }
        if (dto.getSignupEnd().isAfter(dto.getStartTime())) {
            throw new BizException("报名截止时间不能晚于活动开始时间");
        }
    }
}
