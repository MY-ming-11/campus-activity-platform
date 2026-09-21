package com.luosh.activity.vo;

/**
 * 大屏展示用的签到码。secondsLeft 用来驱动倒计时。
 */
public record CheckinCodeVO(String code, long secondsLeft) {
}
