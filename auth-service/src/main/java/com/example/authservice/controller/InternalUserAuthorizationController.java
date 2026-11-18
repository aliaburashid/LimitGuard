package com.example.authservice.controller;

import com.example.authservice.dto.NotificationRecipientResponse;
import com.example.authservice.dto.UserAuthorizationResponse;
import com.example.authservice.enums.UserStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.authservice.dto.InternalUserDisplayResponse;
import com.example.authservice.model.User;
import com.example.authservice.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;

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
@SecurityScheme(name = "Activity Recipient Key", type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER, paramName = "X-Activity-Service-Key")
@RequiredArgsConstructor
public class InternalUserAuthorizationController {

    // Gives the controller access to user-related business logic
    private final UserService userService;

    private final UserRepository userRepository;

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

    // Returns display information for a user in the caller's institution.
    @GetMapping("/users/{userId}/display")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InternalUserDisplayResponse> getUserDisplay(
            @PathVariable Long userId,
            @RequestHeader(value = "X-Internal-Service-Key", required = false)
            String providedServiceKey) {

        // Only trusted internal services may call this endpoint.
        if (providedServiceKey == null
                || !MessageDigest.isEqual(
                        internalServiceKey.getBytes(StandardCharsets.UTF_8),
                        providedServiceKey.getBytes(StandardCharsets.UTF_8))) {

            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        // The authenticated caller's institution comes from Auth Service.
        UserAuthorizationResponse caller =
                userService.getCurrentUserAuthorization();

        // Find the requested user.
        User requestedUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        // Prevent cross-institution information access.
        if (!requestedUser.getFinancialInstitution().getId()
                .equals(caller.getFinancialInstitutionId())) {

            throw new AccessDeniedException(
                    "Cannot access users from another financial institution");
        }

        InternalUserDisplayResponse response =
                new InternalUserDisplayResponse(
                        requestedUser.getId(),
                        requestedUser.getFirstName() + " "
                                + requestedUser.getLastName(),
                        requestedUser.getFinancialInstitution().getId(),
                        requestedUser.getFinancialInstitution().getName()
                );

        return ResponseEntity.ok(response);
    }

    // Find the original requester for Activity's credit notification.
    @GetMapping("/notification-recipient")
    @SecurityRequirement(name = "Activity Recipient Key")
    public ResponseEntity<NotificationRecipientResponse> getNotificationRecipient(
            @RequestParam Long requesterId,
            @RequestParam Long financialInstitutionId) {

        // Return the same response for missing, mismatched or ineligible accounts.
        User requester = userRepository.findById(requesterId)
                .filter(user -> financialInstitutionId.equals(user.getFinancialInstitution().getId()))
                .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.isEmailVerified())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient not found"));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new NotificationRecipientResponse(requester.getEmail()));
    }
}
