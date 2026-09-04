package com.yf.ability.shiro.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.yf.base.utils.file.MD5Util;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

/**
 * JWT工具类
 *
 * @author bool
 */
@Component
public class JwtUtils {


    /**
     * 有效期24小时
     */
    private final int expireHours;

    private final String secret;

    public JwtUtils(@Value("${security.jwt.secret:}") String secret,
                    @Value("${security.jwt.expire-hours:24}") int expireHours) {
        if (StringUtils.isBlank(secret) || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET 未配置或长度不足32个字符，拒绝启动");
        }
        if (expireHours <= 0) {
            throw new IllegalStateException("security.jwt.expire-hours 必须大于 0");
        }
        this.secret = secret;
        this.expireHours = expireHours;
    }

    /**
     * 校验是否正确
     *
     * @param token
     * @param username
     * @return
     */
    public boolean verify(String token, String username) {
        try {
            if (StringUtils.isAnyBlank(token, username)) {
                return false;
            }
            // 根据密码生成JWT效验器
            Algorithm algorithm = Algorithm.HMAC256(encryptSecret(username));
            JWTVerifier verifier = JWT.require(algorithm)
                    .withClaim("username", username)
                    .build();
            // 效验TOKEN
            verifier.verify(token);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }


    /**
     * 从Token中解密获得用户名
     *
     * @param token
     * @return
     */
    public String getUsername(String token) {
        DecodedJWT jwt = JWT.decode(token);
        return jwt.getClaim("username").asString();
    }

    /**
     * 解析并校验签名，只有通过校验的用户名才可用于后续会话查询。
     */
    public String getVerifiedUsername(String token) {
        String username = getUsername(token);
        if (!verify(token, username)) {
            throw new IllegalArgumentException("无效的token");
        }
        return username;
    }

    /**
     * 生成JWT Token字符串
     *
     * @param username
     * @return
     */
    public String sign(String username) {
        return sign(username, null);
    }

    /**
     * 生成不晚于业务截止时间失效的 Token。
     */
    public String sign(String username, Date maxExpiresAt) {
        Calendar cl = Calendar.getInstance();
        cl.setTimeInMillis(System.currentTimeMillis());
        Date issuedAt = cl.getTime();
        cl.add(Calendar.HOUR, expireHours);
        Date expiresAt = cl.getTime();
        if (maxExpiresAt != null && maxExpiresAt.before(expiresAt)) {
            expiresAt = maxExpiresAt;
        }
        if (!expiresAt.after(issuedAt)) {
            throw new IllegalArgumentException("Token 截止时间必须晚于当前时间");
        }

        Algorithm algorithm = Algorithm.HMAC256(encryptSecret(username));
        // 附带username信息
        return JWT.create()
                .withClaim("username", username)
                .withIssuedAt(issuedAt)
                .withJWTId(UUID.randomUUID().toString())
                .withExpiresAt(expiresAt).sign(algorithm);
    }

    /**
     * 根据用户名和秘钥，生成一个新的秘钥，用于JWT加强一些安全性
     *
     * @param userName
     * @return
     */
    private String encryptSecret(String userName) {
        return MD5Util.MD5(userName + "&" + secret);
    }


    /**
     * 判断Token是否到期
     *
     * @param token
     * @return
     */
    public boolean expired(String token) {
        DecodedJWT jwt = JWT.decode(token);
        Date expiresAt = jwt.getExpiresAt();
        return expiresAt == null || !expiresAt.after(new Date());
    }

    /**
     * 当前 token 剩余有效秒数，用于让 Redis 会话与 JWT 同时过期。
     */
    public long remainingSeconds(String token) {
        Date expiresAt = JWT.decode(token).getExpiresAt();
        if (expiresAt == null) {
            return 0L;
        }
        return Math.max(0L, (expiresAt.getTime() - System.currentTimeMillis() + 999L) / 1000L);
    }
}
