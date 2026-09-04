package com.yf.modules.exam.assignment.service;

import com.yf.base.api.exception.ServiceException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class AccessCodeManager {

    static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final int CODE_LENGTH = 6;
    static final int ITERATIONS = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] pepper;

    public AccessCodeManager(@Value("${security.assignment-code.pepper:}") String pepper) {
        if (StringUtils.isBlank(pepper) || pepper.length() < 32) {
            throw new IllegalStateException("ASSIGNMENT_CODE_PEPPER 未配置或长度不足32个字符，拒绝启动");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public String generate() {
        StringBuilder value = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return value.toString();
    }

    public String normalize(String rawCode) {
        String code = StringUtils.trimToEmpty(rawCode).toUpperCase(Locale.ROOT);
        if (code.length() != CODE_LENGTH || code.chars().anyMatch(ch -> ALPHABET.indexOf(ch) < 0)) {
            throw new ServiceException("考核信息不存在或凭证错误！");
        }
        return code;
    }

    public String lookup(String normalizedCode) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalizedCode.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("考核码摘要初始化失败", e);
        }
    }

    public String hash(String normalizedCode) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(normalizedCode, salt, ITERATIONS);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(derived);
    }

    public boolean matches(String normalizedCode, String encoded) {
        try {
            String[] parts = StringUtils.defaultString(encoded).split(":");
            if (parts.length != 3) {
                return false;
            }
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            return MessageDigest.isEqual(expected, derive(normalizedCode, salt, iterations));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private byte[] derive(String code, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(code.toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("考核码哈希失败", e);
        } finally {
            spec.clearPassword();
        }
    }
}
