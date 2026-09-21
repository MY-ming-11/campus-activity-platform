package com.luosh.activity.service;

import com.luosh.activity.common.BizException;
import com.luosh.activity.common.RedisKeys;
import com.luosh.activity.entity.Activity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 名额的 Redis 操作。
 *
 * 整个方案的核心在这句话上：**Redis 执行 Lua 是按顺序、原子的，中间不会被别的请求插进来。**
 * 所以"查重 + 判断余量 + 扣减"这三步必须写在一段 Lua 里。
 * 如果拆成三次 Redis 调用，1000 个人同时点报名就会出现超卖。
 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    /** Lua 返回码 */
    public static final int OK = 0;
    public static final int SOLD_OUT = 1;
    public static final int ALREADY = 2;
    private static final int NOT_INIT = -1;

    private static final String RESERVE_LUA = """
            local stock = redis.call('GET', KEYS[1])
            if not stock then return {-1, 0} end
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
                return {2, tonumber(stock)}
            end
            stock = tonumber(stock)
            if stock <= 0 then return {1, 0} end
            stock = redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            return {0, stock}
            """;

    /**
     * 释放名额。
     * SREM 返回 1 才回补余量——这一句是防重复释放的：
     * 同一个用户调两次取消，第二次 SREM 返回 0，余量不会被加两遍，
     * 否则余量会被撑到超过活动名额上限。
     */
    private static final String RELEASE_LUA = """
            if redis.call('SREM', KEYS[2], ARGV[1]) == 1 then
                return redis.call('INCR', KEYS[1])
            end
            return -1
            """;

    private static final DefaultRedisScript<List> RESERVE_SCRIPT =
            new DefaultRedisScript<>(RESERVE_LUA, List.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(RELEASE_LUA, Long.class);

    private final StringRedisTemplate redis;

    /**
     * 名额第一次被访问时从活动配置初始化。
     * 用 setIfAbsent 而不是 set，是为了并发时只有一个请求真正写入。
     */
    public void initIfAbsent(Long activityId, Integer quota) {
        if (quota == null) {
            return;
        }
        redis.opsForValue().setIfAbsent(RedisKeys.activityStock(activityId), String.valueOf(quota));
    }

    /**
     * 批量回填剩余名额，给活动列表用。
     * 用 multiGet 一次往返拿完，而不是循环调用 N 次。
     */
    public void fillRemaining(List<Activity> activities) {
        if (activities == null || activities.isEmpty()) {
            return;
        }
        List<String> keys = activities.stream()
                .map(a -> RedisKeys.activityStock(a.getId()))
                .toList();
        List<String> values = redis.opsForValue().multiGet(keys);

        for (int i = 0; i < activities.size(); i++) {
            Activity activity = activities.get(i);
            String value = values == null ? null : values.get(i);
            if (value == null) {
                initIfAbsent(activity.getId(), activity.getQuota());
                activity.setRemaining(activity.getQuota() == null ? 0L : activity.getQuota().longValue());
            } else {
                activity.setRemaining(Long.parseLong(value));
            }
        }
    }

    /** 原子预扣一个名额。 */
    public ReserveResult reserve(Long activityId, Long userId, Integer quota) {
        initIfAbsent(activityId, quota);

        List<Long> result = redis.execute(RESERVE_SCRIPT,
                List.of(RedisKeys.activityStock(activityId), RedisKeys.activityUsers(activityId)),
                String.valueOf(userId));

        if (result == null || result.size() < 2) {
            throw new BizException(500, "名额预扣失败，请重试");
        }
        int code = ((Number) result.get(0)).intValue();
        long remaining = ((Number) result.get(1)).longValue();

        if (code == NOT_INIT) {
            // 正常情况下不会走到：报名前刚初始化过。真出现说明 key 被外部清掉了。
            throw new BizException(500, "名额数据未初始化，请重试");
        }
        return new ReserveResult(code, remaining);
    }

    /** 释放一个名额，返回是否真的释放了。 */
    public boolean release(Long activityId, Long userId) {
        Long result = redis.execute(RELEASE_SCRIPT,
                List.of(RedisKeys.activityStock(activityId), RedisKeys.activityUsers(activityId)),
                String.valueOf(userId));
        return result != null && result >= 0;
    }

    /**
     * 管理员改了名额上限后，把 Redis 余量同步成 新上限 - 已报名人数。
     * 不做这一步的话，改了名额 Redis 还是老值，两边就对不上了。
     */
    public void syncStock(Long activityId, Integer quota, long occupied) {
        long remaining = Math.max(0, quota - occupied);
        redis.opsForValue().set(RedisKeys.activityStock(activityId), String.valueOf(remaining));
    }

    public long remaining(Long activityId, Integer quota) {
        initIfAbsent(activityId, quota);
        String value = redis.opsForValue().get(RedisKeys.activityStock(activityId));
        return value == null ? (quota == null ? 0 : quota) : Long.parseLong(value);
    }

    /** 预扣结果：返回码 + 扣减后的剩余名额 */
    public record ReserveResult(int code, long remaining) {

        public boolean isOk() {
            return code == OK;
        }

        public boolean isSoldOut() {
            return code == SOLD_OUT;
        }

        public boolean isAlready() {
            return code == ALREADY;
        }
    }
}
