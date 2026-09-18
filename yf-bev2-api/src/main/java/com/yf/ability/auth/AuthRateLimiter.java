package com.yf.ability.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Shared fixed windows. Redis performs the increment and expiry in one atomic operation. */
@Component
@RequiredArgsConstructor
public class AuthRateLimiter {
    private final StringRedisTemplate redis;
    static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
        local count = tonumber(redis.call('GET', KEYS[1]) or '0')
        local ttl = redis.call('TTL', KEYS[1])
        if count >= tonumber(ARGV[1]) and ttl >= 0 then return math.max(1, ttl) end
        if ttl < 0 then
          redis.call('SET', KEYS[1], 1, 'EX', ARGV[2])
        else
          redis.call('INCR', KEYS[1])
        end
        return 0
        """, Long.class);

    public String check(String scope, String identity, int maximum, int seconds) {
        String key = "security:auth:v1:" + scope + ":" + digest(identity);
        Long retry;
        try {
            retry = redis.execute(SCRIPT, List.of(key), String.valueOf(maximum), String.valueOf(seconds));
        } catch (RuntimeException e) { throw new AuthUnavailableException(); }
        if (retry == null) throw new AuthUnavailableException();
        if (retry > 0) throw new AuthRateLimitException(retry);
        return key;
    }

    public void clear(String key) {
        try { redis.delete(key); }
        catch (RuntimeException e) { throw new AuthUnavailableException(); }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
