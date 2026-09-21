package com.luosh.activity.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luosh.activity.common.BizException;
import com.luosh.activity.common.HmacUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 手写 JWT，不引第三方库。
 *
 * 结构： base64Url(header) . base64Url(payload) . base64Url(HMAC-SHA256(前两段))
 *
 * 两个容易被追问的点：
 * 1. 验签用 MessageDigest.isEqual 而不是 Arrays.equals，前者是常量时间比较，避免时序攻击。
 * 2. payload 只是 Base64 编码，不是加密。所以里面绝不能放敏感信息，只放 userId 和 role。
 */
@Component
public class JwtUtil {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER =
            ENCODER.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] secretKey;
    private final long expireMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-minutes}") long expireMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret 长度至少需要 32 字节");
        }
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.expireMillis = expireMinutes * 60_000L;
    }

    /** 签发 token。 */
    public String sign(Long userId, String role) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uid", userId);
        payload.put("role", role);
        payload.put("exp", System.currentTimeMillis() + expireMillis);
        try {
            String body = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String content = HEADER + "." + body;
            return content + "." + ENCODER.encodeToString(hmac(content));
        } catch (Exception e) {
            throw new IllegalStateException("签发 token 失败", e);
        }
    }

    /** 校验并解析 token，任何一步不通过都抛 401。 */
    public LoginUser parse(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BizException(401, "token 格式错误");
        }
        String content = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(hmac(content), DECODER.decode(parts[2]))) {
            throw new BizException(401, "token 签名无效");
        }
        try {
            JsonNode node = objectMapper.readTree(DECODER.decode(parts[1]));
            if (node.path("exp").asLong() < System.currentTimeMillis()) {
                throw new BizException(401, "登录已过期，请重新登录");
            }
            return new LoginUser(node.path("uid").asLong(), node.path("role").asText());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(401, "token 解析失败");
        }
    }

    private byte[] hmac(String content) {
        return HmacUtil.hmacSha256(secretKey, content);
    }
}
