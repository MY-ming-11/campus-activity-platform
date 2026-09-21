package com.luosh.activity.vo;

/**
 * 报名结果。
 * success 区分"这次真的抢到了"和"你本来就已经报名了"，
 * 两者对用户都是成功，前端提示文案不同。
 */
public record SignupResultVO(boolean success, long remaining, String message) {
}
