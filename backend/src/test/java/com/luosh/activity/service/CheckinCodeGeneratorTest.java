package com.luosh.activity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 签到码算法的单元测试。
 *
 * 这个类是纯逻辑，不依赖 Spring、数据库、Redis，所以能跑得很快，
 * 也最适合放进 CI —— 每次提交都验证一遍核心安全算法没被改坏。
 */
class CheckinCodeGeneratorTest {

    private static final Long ACTIVITY_ID = 42L;
    private static final String SECRET = "3f8a1c9d2b7e4a6f8c0d1e2f3a4b5c6d";
    private static final long SLOT = 29_000_000L;

    private final CheckinCodeGenerator generator = new CheckinCodeGenerator();

    @Test
    @DisplayName("同一活动、同一密钥、同一时间片，生成的码必须完全一致")
    void sameInputsProduceSameCode() {
        // 这条看着废话，但它是「多实例部署」的前提：
        // 服务端没有存签到码，每次都是现算的，算出来的必须一致。
        String first = generator.codeOf(ACTIVITY_ID, SECRET, SLOT);
        String second = generator.codeOf(ACTIVITY_ID, SECRET, SLOT);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("时间片变了，签到码跟着变")
    void differentSlotProducesDifferentCode() {
        String current = generator.codeOf(ACTIVITY_ID, SECRET, SLOT);
        String next = generator.codeOf(ACTIVITY_ID, SECRET, SLOT + 1);

        assertThat(next).isNotEqualTo(current);
    }

    @Test
    @DisplayName("同一时间片下，不同活动的签到码互不相通")
    void differentActivityProducesDifferentCode() {
        String forActivity42 = generator.codeOf(42L, SECRET, SLOT);
        String forActivity43 = generator.codeOf(43L, SECRET, SLOT);

        assertThat(forActivity43).isNotEqualTo(forActivity42);
    }

    @Test
    @DisplayName("密钥不同，就算活动和时间片相同，码也不同")
    void differentSecretProducesDifferentCode() {
        String withSecretA = generator.codeOf(ACTIVITY_ID, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", SLOT);
        String withSecretB = generator.codeOf(ACTIVITY_ID, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", SLOT);

        assertThat(withSecretB).isNotEqualTo(withSecretA);
    }

    @Test
    @DisplayName("签到码固定是 6 位数字，不足 6 位前面补零")
    void codeIsAlwaysSixDigits() {
        for (long slot = SLOT; slot < SLOT + 200; slot++) {
            assertThat(generator.codeOf(ACTIVITY_ID, SECRET, slot))
                    .matches("\\d{6}");
        }
    }

    @Test
    @DisplayName("当前时间片的码可以用")
    void acceptsCurrentSlot() {
        String code = generator.codeOf(ACTIVITY_ID, SECRET, SLOT);

        assertThat(generator.accepts(ACTIVITY_ID, SECRET, code, SLOT)).isTrue();
    }

    @Test
    @DisplayName("上一个时间片的码仍然可以用（容错窗口）")
    void acceptsPreviousSlot() {
        String previousSlotCode = generator.codeOf(ACTIVITY_ID, SECRET, SLOT - 1);

        // 学生刚看到码、走到输入框时刚好翻页，不该白输一次
        assertThat(generator.accepts(ACTIVITY_ID, SECRET, previousSlotCode, SLOT)).isTrue();
    }

    @Test
    @DisplayName("再往前一个时间片的码就失效了")
    void rejectsOlderSlot() {
        String twoSlotsAgo = generator.codeOf(ACTIVITY_ID, SECRET, SLOT - 2);

        assertThat(generator.accepts(ACTIVITY_ID, SECRET, twoSlotsAgo, SLOT)).isFalse();
    }

    @Test
    @DisplayName("别的活动的码不能拿来签到自己身上")
    void rejectsCodeFromAnotherActivity() {
        String otherActivityCode = generator.codeOf(999L, SECRET, SLOT);

        assertThat(generator.accepts(ACTIVITY_ID, SECRET, otherActivityCode, SLOT)).isFalse();
    }

    @Test
    @DisplayName("空码直接判无效，不抛异常")
    void rejectsNullCode() {
        assertThat(generator.accepts(ACTIVITY_ID, SECRET, null, SLOT)).isFalse();
    }

    @Test
    @DisplayName("倒计时恒在 1 到 30 秒之间")
    void secondsLeftIsWithinSlot() {
        for (int i = 0; i < 50; i++) {
            assertThat(generator.secondsLeftInSlot()).isBetween(1L, CheckinCodeGenerator.SLOT_SECONDS);
        }
    }

    @Test
    @DisplayName("连续 30 秒内的码不会大面积重复")
    void codesDoNotCollideOften() {
        // 6 位数字理论上会碰撞，这里只验证不是「每次都一样」这种明显错误
        long distinct = java.util.stream.LongStream.range(0, 60)
                .mapToObj(offset -> generator.codeOf(ACTIVITY_ID, SECRET, SLOT + offset))
                .distinct()
                .count();

        assertThat(distinct).isGreaterThanOrEqualTo(55);
    }
}
