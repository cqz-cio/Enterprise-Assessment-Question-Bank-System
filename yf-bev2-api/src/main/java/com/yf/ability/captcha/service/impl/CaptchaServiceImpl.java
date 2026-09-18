package com.yf.ability.captcha.service.impl;

import com.yf.ability.auth.AuthUnavailableException;
import com.yf.ability.captcha.service.CaptchaService;
import com.yf.base.api.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {
    private final StringRedisTemplate redis;
    private static final String PREFIX = "sys:captcha:";
    private boolean validKey(String key) {
        return key != null && key.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}");
    }
    @Override
    public void saveCaptcha(String key, String value) {
        if (!validKey(key)) throw new ServiceException("验证码标识无效，请刷新！");
        Boolean saved;
        try { saved = redis.opsForValue().setIfAbsent(PREFIX + key.toLowerCase(java.util.Locale.ROOT), value, Duration.ofMinutes(5)); }
        catch (RuntimeException e) { throw new AuthUnavailableException(); }
        if (saved == null) throw new AuthUnavailableException();
        if (!saved) throw new ServiceException("请使用新的验证码标识！");
    }
    @Override
    public boolean checkCaptcha(String key, String input) {
        if (!validKey(key)) return false;
        String value;
        // A challenge is consumed atomically on every verification, including wrong answers.
        try { value = redis.opsForValue().getAndDelete(PREFIX + key.toLowerCase(java.util.Locale.ROOT)); }
        catch (RuntimeException e) { throw new AuthUnavailableException(); }
        return value != null && input != null && input.length() <= 16 && value.equalsIgnoreCase(input.trim());
    }
}
