package com.luosh.activity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityDTO {

    @NotBlank(message = "活动标题不能为空")
    @Size(max = 64, message = "活动标题最长 64 个字符")
    private String title;

    private String cover;

    private String description;

    private String location;

    @NotNull(message = "活动开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "活动结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "报名开始时间不能为空")
    private LocalDateTime signupStart;

    @NotNull(message = "报名截止时间不能为空")
    private LocalDateTime signupEnd;

    @NotNull(message = "名额不能为空")
    @Min(value = 1, message = "名额必须大于 0")
    private Integer quota;

    /** DRAFT / PUBLISHED / CLOSED，不传默认 DRAFT */
    private String status;
}
