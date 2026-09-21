package com.luosh.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ManualCheckinDTO {

    @NotNull(message = "缺少报名记录 id")
    private Long signupId;
}
