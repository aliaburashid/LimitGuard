package com.example.limitguard.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    // Test 1: A protected endpoint must reject requests without a JWT
    @Test
    void shouldReturn401WhenJwtIsMissing() throws Exception {

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // Test 2: A protected endpoint must reject an invalid JWT
    @Test
    void shouldReturn401WhenJwtIsInvalid() throws Exception {

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }


    // Read the test-only JWT secret from application-test.properties
    @Value("${jwt-secret}")
    private String jwtSecret;

    // Test 3: An expired JWT must return HTTP 401
    @Test
    void shouldReturn401WhenJwtIsExpired() throws Exception {

        // Create the signing key using our test secret
        SecretKey key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8)
        );

        // Set both timestamps in the past
        Date issuedAt = new Date(System.currentTimeMillis() - 120000);
        Date expiredAt = new Date(System.currentTimeMillis() - 60000);

        // Create a correctly signed JWT that has already expired
        String expiredToken = Jwts.builder()
                .subject("test@example.com")
                .issuedAt(issuedAt)
                .expiration(expiredAt)
                .signWith(key)
                .compact();

        // Send the expired JWT to a protected endpoint
        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }


    // Test 4: An authenticated user without ADMIN permission gets HTTP 403
    @Test
    @WithMockUser(
            username = "manager@example.com",
            roles = {"RELATIONSHIP_MANAGER"}
    )
    void shouldReturn403WhenUserHasInsufficientPermissions() throws Exception {

        // A RELATIONSHIP_MANAGER is authenticated but is not an ADMIN
        // Only ADMIN users can create financial institutions
        mockMvc.perform(post("/api/financial-institutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "LG84 Authorization Test",
                                    "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isForbidden());
    }


}
