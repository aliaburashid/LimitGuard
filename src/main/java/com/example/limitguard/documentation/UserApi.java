package com.example.limitguard.documentation;

import com.example.limitguard.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface UserApi {

    @Operation(
            summary = "Register a new user",
            description = """
                    Creates a new LimitGuard user account.

                    **Registration Rules:**
                    - Required registration details must be provided.
                    - The password must satisfy LimitGuard's password validation requirements.
                    - The email address must not already be registered.
                    - The user must verify their email address before logging in.

                    **Next Step:**
                    - A verification token is sent to the user's email address.
                    - The user must verify the email before accessing authenticated features.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid registration details or weak password"),
            @ApiResponse(responseCode = "409", description = "Email address already registered")
    })
    ResponseEntity<RegisterResponse> registerUser(RegisterRequest registrationDetails);

    @Operation(
            summary = "Verify user email address",
            description = """
                    Verifies a user's email address using the token sent during registration.

                    **Verification Rules:**
                    - A valid verification token must be supplied.
                    - Missing, invalid, or expired tokens cannot be used.
                    - Successful verification enables the user to log in, provided the account is otherwise eligible.

                    **Next Step:**
                    - The user can log in using their registered email address and password.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email verified successfully"),
            @ApiResponse(responseCode = "400", description = "Missing, invalid or expired verification token")
    })
    ResponseEntity<String> verifyEmail(
            @Parameter(description = "Email verification token")
            String token
    );

    @Operation(
            summary = "Log in to LimitGuard",
            description = """
                    Authenticates a registered user using their email address and password.

                    **Authentication Rules:**
                    - The supplied credentials must be valid.
                    - The user's email address must be verified.
                    - The account must be active.

                    **Successful Response:**
                    - Returns a JWT access token.
                    - The token is used to authenticate subsequent protected API requests.
                    - Send the token using the HTTP Bearer authentication scheme.

                    **Security:**
                    - Invalid credentials return `401 Unauthorized`.
                    - An unverified or deactivated account returns `403 Forbidden`.
                    """
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
            description = """
                    Requests a password reset email for a user account.

                    **Process:**
                    - Provide the email address associated with the account.
                    - The password-reset process sends an email containing a reset token.
                    - The token is used with the Reset User Password endpoint to set a new password.

                    **Security:**
                    - Password-reset tokens should be treated as sensitive credentials.
                    - The actual token validity period is determined by the application configuration.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset request processed"),
            @ApiResponse(responseCode = "400", description = "Invalid request body or missing email address")
    })
    ResponseEntity<String> forgotPassword(ForgotPasswordRequest forgotPasswordDetails);

    @Operation(
            summary = "Reset user password",
            description = """
                    Sets a new password using a password-reset token received by email.

                    **Requirements:**
                    - Provide a valid password-reset token.
                    - The new password must satisfy LimitGuard's password validation requirements.
                    - Invalid or expired reset tokens cannot be used.

                    **Result:**
                    - The password is updated when the reset request succeeds.
                    """
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
            description = """
                    Changes the password of the currently authenticated user.

                    **Requirements:**
                    - The user must be authenticated.
                    - The password change request must contain valid information.
                    - The new password must satisfy LimitGuard's password validation requirements.

                    **Security:**
                    - Requires an authenticated user.
                    - Uses the current user's identity rather than a user ID supplied in the request.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid password change request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    ResponseEntity<String> changePassword(ChangePasswordRequest changePasswordRequest);

    @Operation(
            summary = "Get current user profile",
            description = """
                    Retrieves the profile belonging to the currently authenticated user.

                    **Features:**
                    - Returns the current user's profile information.
                    - Uses the authenticated identity to determine which profile to retrieve.

                    **Security:**
                    - Requires an authenticated user.
                    - Does not accept another user's ID to select a profile.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    ResponseEntity<UserProfileResponse> getMyProfile();

    @Operation(
            summary = "Update current user profile",
            description = """
                    Updates the profile belonging to the currently authenticated user.

                    **Features:**
                    - Accepts profile changes through the update profile request.
                    - Applies the changes to the authenticated user's profile.

                    **Security:**
                    - Requires an authenticated user.
                    - The profile is selected using the current user's identity.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid profile update request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    ResponseEntity<UserProfileResponse> updateMyProfile(UpdateProfileRequest updateProfileRequest);

    @Operation(
            summary = "Upload current user profile picture",
            description = """
                    Uploads or replaces the profile picture of the currently authenticated user.

                    **Requirements:**
                    - The uploaded image must be accepted by the application's upload validation.
                    - The upload must be processed successfully to update the profile picture.

                    **Security:**
                    - Requires an authenticated user.
                    - The image is associated with the current user's profile.
                    """
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
            description = """
                    Deactivates an existing user account.

                    **Business Rules:**
                    - The target user must exist.
                    - Deactivation changes the user's account availability according to the application's account-status rules.
                    - A deactivated user cannot log in if the authentication logic enforces the account's inactive status.

                    **Security:**
                    - Requires JWT Bearer authentication.
                    - Required role: `ADMIN`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User deactivated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid deactivation request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    ResponseEntity<String> deactivateUser(
            @Parameter(description = "ID of the user to deactivate", example = "1")
            Long userId,
            DeactivateUserRequest deactivateUserRequest
    );

    @Operation(
            summary = "Update user role (ADMIN only)",
            description = """
                    Changes the role assigned to an existing user account.

                    **Business Rules:**
                    - The target user must exist.
                    - The requested role must be valid according to the application's role configuration.
                    - The updated role determines which role-protected operations the user may access.

                    **Security:**
                    - Requires JWT Bearer authentication.
                    - Required role: `ADMIN`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User role updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid role update request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    ResponseEntity<String> updateUserRole(
            @Parameter(description = "ID of the user whose role will be updated", example = "1")
            Long userId,
            UpdateUserRoleRequest updateUserRoleRequest
    );
}
