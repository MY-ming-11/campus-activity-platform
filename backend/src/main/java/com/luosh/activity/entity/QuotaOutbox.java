package com.luosh.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表。Redis 扣减名额成功但数据库还没落库时，先记一条，
 * 由定时任务兜底补偿，保证"扣了名额但没报名成功"不会永久存在。
 */
@Data
@TableName("quota_outbox")
public class QuotaOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long userId;

    /** PENDING / DONE / FAILED */
    private String status;

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
