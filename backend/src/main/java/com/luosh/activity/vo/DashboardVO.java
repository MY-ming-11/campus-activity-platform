package com.luosh.activity.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端数据看板。一次请求把页面需要的东西全给了，前端不用打四五个接口。
 */
@Data
public class DashboardVO {

    private Overview overview;

    /** 最近 14 天每天的报名数，没有报名的那天补 0，前端可以直接画 */
    private List<TrendPoint> trend;

    /** 按报名数排序的活动排行 */
    private List<ActivityStatVO> activities;

    public record Overview(long activityTotal,
                           long publishedTotal,
                           long signupTotal,
                           long checkinTotal,
                           long attendRate) {
    }

    @Data
    public static class TrendPoint {

        /** yyyy-MM-dd */
        private String date;

        private long count;
    }

    @Data
    public static class ActivityStatVO {

        private Long id;

        private String title;

        private String status;

        private LocalDateTime startTime;

        private Integer quota;

        /** 有效报名数，已取消的不算 */
        private long signedCount;

        private long checkedCount;

        /**
         * 到场率，百分比。刻意不放进 SQL 聚合里算——
         * 分母为 0 时 SQL 会得到 NULL 或者被除零，放在这里算只有一行代码，也不用写 IFNULL 套一层。
         */
        public long getAttendRate() {
            return signedCount == 0 ? 0 : Math.round(checkedCount * 100.0 / signedCount);
        }
    }
}
