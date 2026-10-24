package com.example.limitguard.security;

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

        // Create the JWT utility we want to test
        JWTUtils jwtUtils = new JWTUtils();

        // Use a test-only secret (at least 32 bytes for HS256)
        String testSecret = "LimitGuardTestSecretKey123456789012345";

        // Set the private jwtSecret field without starting Spring Boot
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", testSecret);

        // Create the signing key using our test secret
        SecretKey key = Keys.hmacShaKeyFor(
                testSecret.getBytes(StandardCharsets.UTF_8)
        );

        // Set the token's timestamps in the past
        Date issuedAt = new Date(System.currentTimeMillis() - 120000);
        Date expiredAt = new Date(System.currentTimeMillis() - 60000);

        // Generate a correctly signed JWT that has already expired
        String expiredToken = Jwts.builder()
                .subject("test@example.com")
                .issuedAt(issuedAt)
                .expiration(expiredAt)
                .signWith(key)
                .compact();

        // Check whether JWTUtils accepts the expired token
        boolean isValid = jwtUtils.validateJwtToken(expiredToken);

        // The expired token must be rejected
        assertFalse(isValid);
    }
}
