package com.luosh.activity.service.strategy;

import com.luosh.activity.entity.Activity;
import com.luosh.activity.mapper.QuotaMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 两个基于数据库的策略共用的工具方法。
 */
final class QuotaSupport {

    private QuotaSupport() {
    }

    /** 一次 GROUP BY 查出所有活动的已报名数，避免按活动循环查询 */
    static Map<Long, Long> occupiedMap(QuotaMapper quotaMapper, List<Activity> activities) {
        List<Long> ids = activities.stream().map(Activity::getId).toList();
        return quotaMapper.countOccupiedGroupBy(ids).stream().collect(Collectors.toMap(
                row -> ((Number) row.get("activityId")).longValue(),
                row -> ((Number) row.get("cnt")).longValue()));
    }

    static void fillByCount(QuotaMapper quotaMapper, List<Activity> activities) {
        if (activities == null || activities.isEmpty()) {
            return;
        }
        Map<Long, Long> occupied = occupiedMap(quotaMapper, activities);
        for (Activity activity : activities) {
            long used = occupied.getOrDefault(activity.getId(), 0L);
            activity.setRemaining(Math.max(0L, activity.getQuota() - used));
        }
    }
}
