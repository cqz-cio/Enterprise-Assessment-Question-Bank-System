package com.yf.ability.auth;

public class AuthRateLimitException extends RuntimeException {
    private final long retryAfter;
    public AuthRateLimitException(long retryAfter) {
        super("验证尝试过于频繁，请稍后再试！");
        this.retryAfter = Math.max(1, retryAfter);
    }
    public long getRetryAfter() { return retryAfter; }
}
