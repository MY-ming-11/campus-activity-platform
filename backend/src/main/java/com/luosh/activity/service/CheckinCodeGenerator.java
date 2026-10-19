package com.luosh.activity.service;

import com.luosh.activity.common.HmacUtil;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 动态签到码的生成与校验。
 *
 * 从 CheckinService 里抽出来单独一个类，就一件事：
 * 让这段核心算法可以被单元测试直接覆盖，不用起 Spring 容器、不用连数据库。
 *
 * 算法：用活动自己的密钥，对「活动ID + 时间片」做 HMAC-SHA256，
 * 取摘要前 4 字节折算成 6 位数字。每 {@link #SLOT_SECONDS} 秒换一次码。
 */
@Component
public class CheckinCodeGenerator {

    /** 时间片长度。改这里就能调整换码频率。 */
    public static final long SLOT_SECONDS = 30;

    /** 当前处在第几个时间片 */
    public long currentSlot() {
        return System.currentTimeMillis() / 1000 / SLOT_SECONDS;
    }

    /** 当前时间片还剩几秒 */
    public long secondsLeftInSlot() {
        long nowSeconds = System.currentTimeMillis() / 1000;
        return SLOT_SECONDS - (nowSeconds % SLOT_SECONDS);
    }

    /**
     * 算某个时间片的签到码。
     *
     * @param activityId 活动 ID，参与签名，所以不同活动的码互不相通
     * @param secret     活动自己的密钥，只存在服务端
     * @param slot       时间片序号
     */
    public String codeOf(Long activityId, String secret, long slot) {
        byte[] mac = HmacUtil.hmacSha256(
                secret.getBytes(StandardCharsets.UTF_8),
                activityId + ":" + slot);

        // 取摘要前 4 字节拼成整数，再折算到 6 位。
        // 用位运算拼而不是取子串，是为了让 4 个字节全部参与，避免分布不均。
        int value = ((mac[0] & 0xFF) << 24)
                | ((mac[1] & 0xFF) << 16)
                | ((mac[2] & 0xFF) << 8)
                | (mac[3] & 0xFF);
        return String.format("%06d", Math.floorMod(value, 1_000_000));
    }

    /**
     * 校验签到码是否有效。
     *
     * 同时接受当前时间片和上一个时间片：学生刚看到码、走到输入框时刚好翻页，
     * 不至于白输一次。再往前的码就不认了。
     */
    public boolean accepts(Long activityId, String secret, String code, long slot) {
        if (code == null) {
            return false;
        }
        return codeOf(activityId, secret, slot).equals(code)
                || codeOf(activityId, secret, slot - 1).equals(code);
    }
}
