package com.yf.system.modules.user.controller;

import com.yf.ability.shiro.dto.SysUserLoginDTO;
import com.yf.system.modules.user.service.SysUserService;
import com.yf.system.modules.user.service.SysUserRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SysUserSessionInfoTest {
    @Test
    void infoRefreshesPermissionsForVerifiedSessionOwner() {
        SysUserService service = mock(SysUserService.class);
        SysUserRoleService roles = mock(SysUserRoleService.class);
        SysUserLoginDTO session = new SysUserLoginDTO();
        session.setId("verified-owner");
        session.setPermissions(List.of("repo:qu:add"));
        when(service.token("test-token")).thenReturn(session);
        when(roles.findUserPermission("verified-owner")).thenReturn(List.of("repo:qu:add", "repo:qu:import"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("token", "test-token");
        request.addParameter("userId", "another-user");
        new SysUserController(service, roles).info(request);
        assertTrue(session.getPermissions().contains("repo:qu:import"));
        verify(roles).findUserPermission("verified-owner");
        verify(roles, never()).findUserPermission("another-user");
    }

    @Test
    void invalidSessionNeverLoadsPermissions() {
        SysUserService service = mock(SysUserService.class);
        SysUserRoleService roles = mock(SysUserRoleService.class);
        when(service.token(null)).thenThrow(new IllegalArgumentException("Invalid session"));
        assertThrows(IllegalArgumentException.class, () ->
            new SysUserController(service, roles).info(new MockHttpServletRequest()));
        verify(roles, never()).findUserPermission(any());
    }
}
