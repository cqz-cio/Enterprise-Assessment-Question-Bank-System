package com.yf.ability.shiro.jwt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private static final String SECRET = "test-only-secret-with-at-least-32-characters";

    @Test
    void signedTokenCanBeVerifiedAndHasRedisTtl() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, 1);

        String token = jwtUtils.sign("employee001");

        assertEquals("employee001", jwtUtils.getVerifiedUsername(token));
        assertTrue(jwtUtils.verify(token, "employee001"));
        assertTrue(jwtUtils.remainingSeconds(token) > 0);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, 1);
        String token = jwtUtils.sign("employee001");
        String tampered = tamperSignature(token);

        assertFalse(jwtUtils.verify(tampered, "employee001"));
        assertThrows(IllegalArgumentException.class, () -> jwtUtils.getVerifiedUsername(tampered));
    }

    @Test
    void differentSecretCannotVerifyToken() {
        JwtUtils signer = new JwtUtils(SECRET, 1);
        JwtUtils verifier = new JwtUtils("another-test-secret-with-at-least-32-chars", 1);

        assertFalse(verifier.verify(signer.sign("employee001"), "employee001"));
    }

    @Test
    void weakSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtUtils("too-short", 1));
    }

    private String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        parts[2] = (first == 'A' ? 'B' : 'A') + parts[2].substring(1);
        return String.join(".", parts);
    }
}
