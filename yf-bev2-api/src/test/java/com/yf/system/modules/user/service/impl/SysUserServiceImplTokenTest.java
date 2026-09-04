package com.yf.system.modules.user.service.impl;

import com.yf.ability.Constant;
import com.yf.ability.captcha.service.CaptchaService;
import com.yf.ability.redis.service.RedisService;
import com.yf.ability.shiro.jwt.JwtUtils;
import com.yf.base.api.exception.ServiceException;
import com.yf.system.modules.config.service.CfgSwitchService;
import com.yf.system.modules.menu.service.SysMenuService;
import com.yf.system.modules.user.entity.SysUser;
import com.yf.system.modules.user.enums.UserState;
import com.yf.system.modules.user.mapper.SysUserMapper;
import com.yf.system.modules.user.dto.request.SysUserLoginReqDTO;
import com.yf.system.modules.user.service.SysUserRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SysUserServiceImplTokenTest {

    private RedisService redisService;
    private JwtUtils jwtUtils;
    private SysUserMapper userMapper;
    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        redisService = mock(RedisService.class);
        jwtUtils = mock(JwtUtils.class);
        userMapper = mock(SysUserMapper.class);
        service = new SysUserServiceImpl(
                mock(SysUserRoleService.class), redisService, mock(CaptchaService.class),
                mock(CfgSwitchService.class), mock(SysMenuService.class), jwtUtils);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
    }

    @Test
    void oldTokenIsRejectedWhenRedisContainsNewSession() {
        when(jwtUtils.getVerifiedUsername("old-token")).thenReturn("employee001");
        when(redisService.getJson(Constant.USER_NAME_KEY + "employee001"))
                .thenReturn(session("current-token"));

        assertThrows(ServiceException.class, () -> service.token("old-token"));
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void exactTokenForActiveUserIsAccepted() {
        when(jwtUtils.getVerifiedUsername("current-token")).thenReturn("employee001");
        when(redisService.getJson(Constant.USER_NAME_KEY + "employee001"))
                .thenReturn(session("current-token"));
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setUserName("employee001");
        user.setState(UserState.NORMAL);
        when(userMapper.selectById("user-1")).thenReturn(user);

        assertEquals("user-1", service.token("current-token").getId());
    }

    @Test
    void disabledUserSessionIsRevoked() {
        when(jwtUtils.getVerifiedUsername("current-token")).thenReturn("employee001");
        when(redisService.getJson(Constant.USER_NAME_KEY + "employee001"))
                .thenReturn(session("current-token"));
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setUserName("employee001");
        user.setState(UserState.DISABLED);
        when(userMapper.selectById("user-1")).thenReturn(user);

        assertThrows(ServiceException.class, () -> service.token("current-token"));
        verify(redisService).del(Constant.USER_NAME_KEY + "employee001");
    }

    @Test
    void blankPasswordCannotBypassLoginAuthentication() {
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setUserName("employee001");
        user.setState(UserState.NORMAL);
        when(userMapper.selectOne(any())).thenReturn(user);
        SysUserLoginReqDTO request = new SysUserLoginReqDTO();
        request.setUserName("employee001");
        request.setPassword("  ");

        assertThrows(ServiceException.class, () -> service.login(request));
        verify(jwtUtils, never()).sign(anyString());
    }

    private Map<String, Object> session(String token) {
        Map<String, Object> session = new HashMap<>();
        session.put("id", "user-1");
        session.put("userName", "employee001");
        session.put("state", UserState.NORMAL);
        session.put("token", token);
        return session;
    }
}
