package com.luosh.activity.common;

/**
 * Redis key 集中定义，避免各处在字符串里手拼 key 导致对不上。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 活动剩余名额，String，初始值 = 活动配置的名额上限 */
    public static String activityStock(Long activityId) {
        return "activity:stock:" + activityId;
    }

    /** 已占用名额的用户集合，Set，既做幂等去重，也是释放名额的依据 */
    public static String activityUsers(Long activityId) {
        return "activity:users:" + activityId;
    }

    /** 单个用户的报名令牌桶 */
    public static String signupRate(Long userId) {
        return "rate:signup:" + userId;
    }

    /** 单个用户的签到尝试令牌桶。签到码只有 6 位，必须防爆破。 */
    public static String checkinRate(Long userId) {
        return "rate:checkin:" + userId;
    }
}
