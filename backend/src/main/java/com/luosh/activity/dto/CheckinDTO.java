package com.luosh.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CheckinDTO {

    @NotBlank(message = "请输入签到码")
    @Pattern(regexp = "^\\d{6}$", message = "签到码是 6 位数字")
    private String code;
}
