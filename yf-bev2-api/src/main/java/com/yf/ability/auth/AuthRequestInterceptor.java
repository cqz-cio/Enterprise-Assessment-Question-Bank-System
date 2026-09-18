package com.yf.ability.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Counts even malformed requests before MVC deserializes their bodies. Never trusts forwarded headers. */
@Component
@RequiredArgsConstructor
public class AuthRequestInterceptor implements HandlerInterceptor {
    private final AuthRateLimiter limiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object mapping = request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = mapping == null ? request.getServletPath() : mapping.toString();
        String scope;
        int limit;
        switch (path) {
            case "/api/sys/user/login" -> { scope = "login-ip"; limit = 300; }
            case "/api/sys/user/reg" -> { scope = "register-ip"; limit = 60; }
            case "/api/exam/assignment/candidate/verify" -> { scope = "candidate-ip"; limit = 300; }
            case "/api/common/captcha/gen" -> { scope = "captcha-ip"; limit = 600; }
            default -> { return true; }
        }
        if (!"OPTIONS".equals(request.getMethod())) limiter.check(scope, request.getRemoteAddr(), limit, 600);
        return true;
    }
}
