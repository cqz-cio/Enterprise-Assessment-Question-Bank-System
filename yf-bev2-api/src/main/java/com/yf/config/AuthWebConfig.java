package com.yf.config;

import com.yf.ability.auth.AuthRequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AuthWebConfig implements WebMvcConfigurer {
    private final AuthRequestInterceptor interceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/sys/user/login", "/api/sys/user/reg",
                "/api/exam/assignment/candidate/verify", "/api/common/captcha/gen");
    }
}
