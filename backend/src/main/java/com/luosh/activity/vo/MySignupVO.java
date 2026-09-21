package com.luosh.activity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的报名列表。用 @Data 的普通类而不是 record，
 * 因为 MyBatis 的结果映射靠 setter 注入，record 没有 setter。
 */
@Data
public class MySignupVO {

    private Long signupId;

    private Long activityId;

    /** SIGNED / CANCELED / CHECKED */
    private String status;

    private LocalDateTime signupTime;

    private String title;

    private String cover;

    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
