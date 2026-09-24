package com.luosh.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String cover;

    private String description;

    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime signupStart;

    private LocalDateTime signupEnd;

    /** 名额上限。真实余量在 Redis，以 Redis 为准，这里是配置值。 */
    private Integer quota;

    /**
     * 已占用名额。只有「乐观锁」报名策略会用它做原子扣减：
     * UPDATE activity SET occupied = occupied + 1 WHERE id = ? AND occupied < quota
     * 影响行数为 1 就是抢到了。其他策略不读也不写这个字段。
     */
    private Integer occupied;

    /**
     * 动态签到码的签名密钥。
     * 绝对不能返回给前端——前端只要能拿到它就能自己签出合法签到码，防代签就失效了。
     */
    @JsonIgnore
    private String checkinSecret;

    /** DRAFT / PUBLISHED / CLOSED */
    private String status;

    private Long creatorId;

    private LocalDateTime createTime;

    /**
     * 剩余名额。不存数据库，查询时从 Redis 实时回填。
     * 加 @TableField(exist = false) 是告诉 MyBatis-Plus 别把它当成一个字段去查。
     */
    @TableField(exist = false)
    private Long remaining;
}
