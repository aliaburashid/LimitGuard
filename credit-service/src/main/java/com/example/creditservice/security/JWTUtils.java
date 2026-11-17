package com.example.creditservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

// Handles creating and checking JWT tokens
@Service
public class JWTUtils {

    // Used to log JWT errors
    Logger logger = Logger.getLogger(JWTUtils.class.getName());

    // Gets the JWT secret from application.properties
    @Value("${jwt-secret}")
    private String jwtSecret;

    // creates the secret key used to sign and check JWT tokens
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // ---------------READ who the token belongs to-------------------------
    // Gets the user's email stored inside the JWT token
    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                // Use our secret key to check the token
                .verifyWith(getSigningKey())
                .build()
                // Read the JWT
                .parseSignedClaims(token)
                .getPayload()
                // Get the email stored as the subject
                .getSubject();
    }

    // ---------------CHECK whether the token is valid   -------------------------
    // Checks if the JWT token is valid
    public boolean validateJwtToken(String authToken) {
        try {

            // Try to read and verify the token using our secret key
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);

            // If no exception happened, the token is valid
            return true;

        } catch (Exception exception) {

            // Log the problem if the token is invalid or expired
            logger.log(
                    Level.SEVERE,
                    "Invalid JWT token: {0}",
                    exception.getMessage()
            );
        }

        return false;
    }
}