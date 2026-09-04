package com.yf.ability.shiro;

import com.yf.ability.shiro.dto.SysUserLoginDTO;
import com.yf.ability.shiro.jwt.JwtToken;
import com.yf.ability.shiro.jwt.JwtUtils;
import com.yf.ability.shiro.service.ShiroUserService;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MyShiroRealmTest {

    private static final String SECRET = "test-only-secret-with-at-least-32-characters";

    @Test
    void realmChecksSignatureBeforeLoadingRedisSession() {
        ShiroUserService userService = mock(ShiroUserService.class);
        JwtUtils jwtUtils = new JwtUtils(SECRET, 1);
        MyShiroRealm realm = new MyShiroRealm(userService, jwtUtils);
        String valid = jwtUtils.sign("employee001");
        String tampered = tamperSignature(valid);

        assertThrows(AuthenticationException.class,
                () -> realm.getAuthenticationInfo(new JwtToken(tampered)));
        verifyNoInteractions(userService);
    }

    @Test
    void validSignedAndBoundTokenAuthenticates() {
        ShiroUserService userService = mock(ShiroUserService.class);
        JwtUtils jwtUtils = new JwtUtils(SECRET, 1);
        MyShiroRealm realm = new MyShiroRealm(userService, jwtUtils);
        String token = jwtUtils.sign("employee001");
        SysUserLoginDTO user = new SysUserLoginDTO();
        user.setId("user-1");
        user.setUserName("employee001");
        when(userService.token(token)).thenReturn(user);

        AuthenticationInfo info = realm.getAuthenticationInfo(new JwtToken(token));

        assertNotNull(info);
        assertSame(user, info.getPrincipals().getPrimaryPrincipal());
        verify(userService).token(token);
    }

    private String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        parts[2] = (first == 'A' ? 'B' : 'A') + parts[2].substring(1);
        return String.join(".", parts);
    }
}
