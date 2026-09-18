package com.yf.ability.auth;

import com.yf.ability.captcha.service.CaptchaService;
import com.yf.ability.captcha.service.impl.CaptchaServiceImpl;
import com.yf.ability.redis.service.RedisService;
import com.yf.ability.shiro.jwt.JwtUtils;
import com.yf.base.api.exception.ServiceException;
import com.yf.base.api.exception.ServiceExceptionHandler;
import com.yf.modules.exam.assignment.service.*;
import com.yf.system.modules.config.service.CfgSwitchService;
import com.yf.system.modules.menu.service.SysMenuService;
import com.yf.system.modules.user.controller.SysUserController;
import com.yf.system.modules.user.dto.request.SysUserLoginReqDTO;
import com.yf.system.modules.user.entity.SysUser;
import com.yf.system.modules.user.service.SysUserRoleService;
import com.yf.system.modules.user.service.SysUserService;
import com.yf.system.modules.user.service.impl.SysUserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthSecurityTest {
    private static final String KEY = "12345678-1234-1234-1234-123456789abc";
    @SuppressWarnings("unchecked")
    private ValueOperations<String,String> values(StringRedisTemplate redis) {
        var values = (ValueOperations<String,String>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values); return values;
    }
    @Test void captchaCannotBeOmitted() {
        var redis = mock(StringRedisTemplate.class); var captcha = new CaptchaServiceImpl(redis);
        assertFalse(captcha.checkCaptcha(null, "1234"));
        assertFalse(captcha.checkCaptcha("", "1234"));
        assertFalse(captcha.checkCaptcha("not-a-uuid", "1234"));
        verifyNoInteractions(redis);
    }
    @Test void correctCaptchaIsSingleUseAndCaseInsensitive() {
        var redis=mock(StringRedisTemplate.class);var values=values(redis);
        when(values.getAndDelete("sys:captcha:"+KEY)).thenReturn("Ab12",null);
        var captcha=new CaptchaServiceImpl(redis);
        assertTrue(captcha.checkCaptcha(KEY,"ab12"));
        assertFalse(captcha.checkCaptcha(KEY,"ab12"));
        verify(values,times(2)).getAndDelete("sys:captcha:"+KEY);
    }
    @Test void wrongCaptchaIsAlsoConsumed() {
        var redis=mock(StringRedisTemplate.class);var values=values(redis);
        when(values.getAndDelete("sys:captcha:"+KEY)).thenReturn("1234",null);
        var captcha=new CaptchaServiceImpl(redis);
        assertFalse(captcha.checkCaptcha(KEY,"9999"));
        assertFalse(captcha.checkCaptcha(KEY,"1234"));
    }
    @Test void captchaCannotOverwriteExistingChallenge() {
        var redis=mock(StringRedisTemplate.class);var values=values(redis);
        when(values.setIfAbsent("sys:captcha:"+KEY,"1234",Duration.ofMinutes(5))).thenReturn(false);
        assertThrows(ServiceException.class,()->new CaptchaServiceImpl(redis).saveCaptcha(KEY,"1234"));
    }
    @Test void captchaStoreFailureIsClosed() {
        var redis=mock(StringRedisTemplate.class);var values=values(redis);
        when(values.getAndDelete(anyString())).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(AuthUnavailableException.class,()->new CaptchaServiceImpl(redis).checkCaptcha(KEY,"1234"));
    }
    @Test void ipLimitIgnoresSpoofedForwardingHeadersAndIncludesMalformedRequests() throws Exception {
        var limiter=mock(AuthRateLimiter.class);
        var request=new MockHttpServletRequest("POST","/api/sys/user/login");
        request.setServletPath("/api/sys/user/login");request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Forwarded-For","198.51.100.1");request.addHeader("Forwarded","for=198.51.100.2");
        request.setContent("{malformed".getBytes());
        new AuthRequestInterceptor(limiter).preHandle(request,new MockHttpServletResponse(),null);
        verify(limiter).check("login-ip","192.0.2.1",300,600);
    }
    @Test void candidateIpLimitDoesNotDependOnCode() {
        var limiter=mock(AuthRateLimiter.class);var request=new MockHttpServletRequest();
        request.setMethod("POST");request.setServletPath("/api/exam/assignment/candidate/verify");request.setRemoteAddr("192.0.2.1");
        new AuthRequestInterceptor(limiter).preHandle(request,new MockHttpServletResponse(),null);
        verify(limiter).check("candidate-ip","192.0.2.1",300,600);
    }
    @Test void codeLimitDoesNotDependOnIpAndDoesNotStorePlainCode() {
        var limiter=mock(AuthRateLimiter.class);var manager=new AccessCodeManager("test-only-pepper-at-least-32-characters");
        var throttle=new CandidateVerificationThrottle(limiter,manager);
        throttle.check("192.0.2.1","ABC234");throttle.check("198.51.100.2","abc234");
        verify(limiter,times(2)).check("candidate-code",manager.lookup("ABC234"),10,600);
    }
    @Test void missingCaptchaRejectedBeforeLoginService() throws Exception {
        var service=mock(SysUserService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new SysUserController(service,mock(SysUserRoleService.class)))
                .setControllerAdvice(new ServiceExceptionHandler()).build();
        mvc.perform(post("/api/sys/user/login").contentType("application/json")
                .content("{\"userName\":\"user\",\"password\":\"password\"}"))
                .andExpect(jsonPath("$.code").value(1));
        verifyNoInteractions(service);
    }
    @Test void throttleReports429AndRetryAfter() throws Exception {
        var service=mock(SysUserService.class);var limiter=mock(AuthRateLimiter.class);
        doThrow(new AuthRateLimitException(45)).when(limiter).check(anyString(),anyString(),anyInt(),anyInt());
        var mvc=MockMvcBuilders.standaloneSetup(new SysUserController(service,mock(SysUserRoleService.class)))
                .addInterceptors(new AuthRequestInterceptor(limiter)).setControllerAdvice(new ServiceExceptionHandler()).build();
        mvc.perform(post("/api/sys/user/login").servletPath("/api/sys/user/login")
                .contentType("application/json").content("{broken"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","45"))
                .andExpect(jsonPath("$.code").value(429));
        verifyNoInteractions(service);
    }
    @Test void failedLimiterStoreReturns503() throws Exception {
        var service=mock(SysUserService.class);var limiter=mock(AuthRateLimiter.class);
        doThrow(new AuthUnavailableException()).when(limiter).check(anyString(),anyString(),anyInt(),anyInt());
        var mvc=MockMvcBuilders.standaloneSetup(new SysUserController(service,mock(SysUserRoleService.class)))
                .addInterceptors(new AuthRequestInterceptor(limiter)).setControllerAdvice(new ServiceExceptionHandler()).build();
        mvc.perform(post("/api/sys/user/login").servletPath("/api/sys/user/login"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503));
        verifyNoInteractions(service);
    }
    @Test void directServiceCallCannotSkipCaptcha() {
        var captcha=mock(CaptchaService.class);var limiter=mock(AuthRateLimiter.class);
        var service=spy(new SysUserServiceImpl(mock(SysUserRoleService.class),mock(RedisService.class),captcha,
                mock(CfgSwitchService.class),mock(SysMenuService.class),mock(JwtUtils.class),limiter));
        var request=new SysUserLoginReqDTO();request.setUserName("known");request.setPassword("password");
        assertThrows(ServiceException.class,()->service.login(request));
        verify(service,never()).getOne(any(),anyBoolean());verifyNoInteractions(limiter);
    }
    @Test void wrongPasswordAfterValidCaptchaUsesStoredUserIdLimit() {
        var captcha=mock(CaptchaService.class);var limiter=mock(AuthRateLimiter.class);
        when(captcha.checkCaptcha(KEY,"1234")).thenReturn(true);
        var service=spy(new SysUserServiceImpl(mock(SysUserRoleService.class),mock(RedisService.class),captcha,
                mock(CfgSwitchService.class),mock(SysMenuService.class),mock(JwtUtils.class),limiter));
        var user=new SysUser();user.setId("user-id");user.setUserName("MixedCase");user.setState(0);
        doReturn(user).when(service).getOne(any(),anyBoolean());
        var request=new SysUserLoginReqDTO();request.setUserName("MIXEDCASE");request.setPassword("");
        request.setCaptchaKey(KEY);request.setCaptchaValue("1234");
        assertThrows(ServiceException.class,()->service.login(request));
        verify(limiter).check("login-account","user-id",10,600);verify(limiter,never()).clear(any());
    }
}
