package com.luosh.activity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 核销台上报名名单的一行。
 */
@Data
public class CheckinRosterVO {

    private Long signupId;

    private Long userId;

    /** SIGNED 未签到 / CHECKED 已签到 */
    private String status;

    private LocalDateTime signupTime;

    private String nickname;

    private String username;

    private String phone;

    /** 为空表示还没签到 */
    private LocalDateTime checkTime;

    /** QR 扫码 / MANUAL 手动 */
    private String checkType;
}
