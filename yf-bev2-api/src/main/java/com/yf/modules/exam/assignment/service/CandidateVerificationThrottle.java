package com.yf.modules.exam.assignment.service;

import com.yf.ability.auth.AuthRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CandidateVerificationThrottle {
    private final AuthRateLimiter limiter;
    private final AccessCodeManager accessCodeManager;
    public String check(String remoteAddress, String rawCode) {
        // IP totals are enforced before body validation by AuthRequestInterceptor.
        String code = accessCodeManager.normalize(rawCode);
        return limiter.check("candidate-code", accessCodeManager.lookup(code), 10, 600);
    }
    public void clear(String key) { limiter.clear(key); }
}
