package com.example.limitguard.documentation;

import com.example.limitguard.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface UserApi {

    @Operation(
            summary = "Register a new user",
            description = "Creates a new LimitGuard user account. "
                    + "The user must verify their email address before logging in."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid registration details or weak password"),
            @ApiResponse(responseCode = "409", description = "Email address already registered")
    })
    ResponseEntity<RegisterResponse> registerUser(RegisterRequest registrationDetails);

    @Operation(
            summary = "Verify user email address",
            description = "Verifies a user's email address using the verification "
                    + "token sent during registration. Once verified, "
                    + "the user can log in to LimitGuard."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email verified successfully"),
            @ApiResponse(responseCode = "400", description = "Missing, invalid or expired verification token")
    })
    ResponseEntity<String> verifyEmail(String token);

    @Operation(
            summary = "Log in to LimitGuard",
            description = "Authenticates a registered user using their email "
                    + "and password. Returns a JWT access token when the "
                    + "credentials are valid and the account is verified."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful. JWT token returned."),
            @ApiResponse(responseCode = "400", description = "Invalid request body or missing required fields"),
            @ApiResponse(responseCode = "401", description = "Invalid email or password"),
            @ApiResponse(responseCode = "403", description = "Email not verified or account deactivated")
    })
    ResponseEntity<LoginResponse> loginUser(LoginRequest loginDetails);

    @Operation(
            summary = "Request a password reset",
            description = "Allows a user to request a password reset email. "
                    + "The email contains a token that can be used "
                    + "to set a new password."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset request processed"),
            @ApiResponse(responseCode = "400", description = "Invalid request body or missing email address")
    })
    ResponseEntity<String> forgotPassword(ForgotPasswordRequest forgotPasswordDetails);

    @Operation(
            summary = "Reset user password",
            description = "Allows a user to set a new password using a "
                    + "password-reset token received by email. "
                    + "The new password must meet LimitGuard's password "
                    + "security requirements."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request details, weak password, or invalid or expired reset token"
            )
    })
    ResponseEntity<String> resetPassword(ResetPasswordRequest resetPasswordDetails);

    @Operation(
            summary = "Change current user password",
            description = "Allows an authenticated user to change their password."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid password change request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    ResponseEntity<String> changePassword(ChangePasswordRequest changePasswordRequest);

    @Operation(
            summary = "Get current user profile",
            description = "Returns the authenticated user profile."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    ResponseEntity<UserProfileResponse> getMyProfile();

    @Operation(
            summary = "Update current user profile",
            description = "Updates the authenticated user profile."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid profile update request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    ResponseEntity<UserProfileResponse> updateMyProfile(UpdateProfileRequest updateProfileRequest);

    @Operation(
            summary = "Upload current user profile picture",
            description = "Uploads or replaces the authenticated user profile picture."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile picture updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid profile picture upload"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "500", description = "Unable to save profile picture")
    })
    ResponseEntity<UserProfileResponse> uploadProfilePicture(MultipartFile image) throws IOException;

    @Operation(
            summary = "Deactivate user (ADMIN only)",
            description = "Deactivates a user account. Requires the ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User deactivated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid deactivation request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    ResponseEntity<String> deactivateUser(
            Long userId,
            DeactivateUserRequest deactivateUserRequest
    );

    @Operation(
            summary = "Update user role (ADMIN only)",
            description = "Changes a user role. Requires the ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User role updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid role update request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    ResponseEntity<String> updateUserRole(
            Long userId,
            UpdateUserRoleRequest updateUserRoleRequest
    );
}
