package com.luosh.activity.service;

import com.luosh.activity.common.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 令牌桶限流，同样是 Lua 实现——读令牌、算补充、扣令牌必须原子，
 * 否则并发下会读到同一个旧值，限流直接失效。
 *
 * 桶容量和补充速率由调用方决定：报名和签到的频次要求不一样，
 * 签到码只有 6 位，更不能让人随便试。
 */
@Service
@RequiredArgsConstructor
public class RateLimiter {

    private static final String TOKEN_BUCKET_LUA = """
            local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens'))
            local ts = tonumber(redis.call('HGET', KEYS[1], 'ts'))
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local rate = tonumber(ARGV[3])

            if tokens == nil then
                tokens = capacity
                ts = now
            end

            local delta = math.max(0, now - ts) / 1000
            tokens = math.min(capacity, tokens + delta * rate)

            local allowed = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            end

            redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
            redis.call('EXPIRE', KEYS[1], 60)
            return allowed
            """;

    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT =
            new DefaultRedisScript<>(TOKEN_BUCKET_LUA, Long.class);

    private final StringRedisTemplate redis;

    /**
     * 取一个令牌。桶是惰性初始化的：第一次访问时装满，之后按速率补充。
     *
     * @param key             桶的 key，同一个 key 共享一个桶
     * @param capacity        桶容量，也决定了允许的突发次数
     * @param refillPerSecond 每秒补充多少令牌
     */
    public boolean tryAcquire(String key, int capacity, int refillPerSecond) {
        Long allowed = redis.execute(TOKEN_BUCKET_SCRIPT,
                List.of(key),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(capacity),
                String.valueOf(refillPerSecond));
        return allowed != null && allowed == 1L;
    }
}
