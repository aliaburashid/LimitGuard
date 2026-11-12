package com.example.authservice.controller;

import com.example.authservice.dto.UserAuthorizationResponse;
import com.example.authservice.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

// only trusted services, such as credit service
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// @RestController tells Spring that this class handles HTTP requests
// and returns responses, usually as JSON.
@RestController
// Defines the common URL prefix for all endpoints in this controller
@RequestMapping("/api/auth/internal")
@RequiredArgsConstructor
public class InternalUserAuthorizationController {

    // Gives the controller access to user-related business logic
    private final UserService userService;

    // Secret key shared between Auth Service and trusted internal services
    @Value("${internal-service-key}")
    private String internalServiceKey;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserAuthorizationResponse> getCurrentUserAuthorization(
            @RequestHeader(value = "X-Internal-Service-Key", required = false)
            String providedServiceKey) {

        // Check that the caller provided the correct internal service key.
        if (providedServiceKey == null
                || !MessageDigest.isEqual(
                internalServiceKey.getBytes(StandardCharsets.UTF_8),
                providedServiceKey.getBytes(StandardCharsets.UTF_8))) {

            // Reject requests from callers without the correct key.
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Forbidden"
            );
        }

        // Retrieve the authenticated user's current permissions.
        UserAuthorizationResponse userAuthorization =
                userService.getCurrentUserAuthorization();

        // Return the information to the trusted service.
        return ResponseEntity.ok(userAuthorization);
    }
}
