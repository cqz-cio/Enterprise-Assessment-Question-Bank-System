package com.yf.modules.exam.assignment.service;

import com.yf.base.api.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class CandidateVerificationThrottle {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final StringRedisTemplate redisTemplate;
    private final AccessCodeManager accessCodeManager;

    public String check(String remoteAddress, String rawCode) {
        String code = accessCodeManager.normalize(rawCode);
        String key = "exam:candidate:verify:" + digest(remoteAddress) + ":" + accessCodeManager.lookup(code);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            throw new ServiceException("验证尝试过于频繁，请稍后再试！");
        }
        return key;
    }

    public void clear(String key) {
        redisTemplate.delete(key);
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
