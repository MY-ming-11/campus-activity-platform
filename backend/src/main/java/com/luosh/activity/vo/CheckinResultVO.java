package com.luosh.activity.vo;

/**
 * 签到结果。success 区分"这次真的签上了"和"你本来就已经签过"。
 */
public record CheckinResultVO(boolean success, String activityTitle, String message) {
}
