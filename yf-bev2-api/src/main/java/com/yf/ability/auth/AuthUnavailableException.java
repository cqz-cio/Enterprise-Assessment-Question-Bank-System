package com.yf.ability.auth;

public class AuthUnavailableException extends RuntimeException {
    public AuthUnavailableException() { super("验证服务暂不可用，请稍后重试！"); }
}
