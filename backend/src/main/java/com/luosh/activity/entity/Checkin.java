package com.luosh.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_checkin")
public class Checkin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long signupId;

    private Long userId;

    private LocalDateTime checkTime;

    /** QR 扫码 / MANUAL 手动 */
    private String checkType;

    private Long operatorId;
}
