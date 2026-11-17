package com.example.authservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JWTUtilsTest {

    @Test
    void shouldRejectExpiredJwtToken() {

        JWTUtils jwtUtils = new JWTUtils();

        String testSecret = "LimitGuardTestSecretKey123456789012345";

        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", testSecret);

        SecretKey key = Keys.hmacShaKeyFor(
                testSecret.getBytes(StandardCharsets.UTF_8)
        );

        Date issuedAt = new Date(System.currentTimeMillis() - 120000);
        Date expiredAt = new Date(System.currentTimeMillis() - 60000);

        String expiredToken = Jwts.builder()
                .subject("test@example.com")
                .issuedAt(issuedAt)
                .expiration(expiredAt)
                .signWith(key)
                .compact();

        boolean isValid = jwtUtils.validateJwtToken(expiredToken);

        assertFalse(isValid);
    }
}
