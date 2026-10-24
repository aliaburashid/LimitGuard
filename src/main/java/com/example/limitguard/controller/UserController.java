
package com.example.limitguard.controller;

import com.example.limitguard.dto.*;
import com.example.limitguard.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

// tells Spring that this class contains API endpoints
@RestController
@RequestMapping("/api/auth/users")
public class UserController {

    // Gives the controller access to the user service
    @Autowired
    private UserService userService;


    // Swagger: explains what this endpoint does
    @Operation(
            summary = "Register a new user",
            description = "Creates a new LimitGuard user account. "
                    + "The user must verify their email address before logging in."
    )

    // Swagger: documents the possible HTTP responses
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "User registered successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid registration details or weak password"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email address already registered"
            )
    })
    // Registers a new user
    @PostMapping("/register")
    // takes the JSON request and checks the validation rules in RegisterRequest
    // ResponseEntity lets us return both the data AND an HTTP status code.
    public ResponseEntity<RegisterResponse> registerUser(@Valid @RequestBody RegisterRequest registrationDetails) {

        // sends the registration details to the service to create the new user
        RegisterResponse newUser = userService.registerUser(registrationDetails);

        // Returns the created user with HTTP status 201 CREATED
        return new ResponseEntity<>(newUser, HttpStatus.CREATED);
    }



    // Swagger: documents email verification
    @Operation(
            summary = "Verify user email address",
            description = "Verifies a user's email address using the verification "
                    + "token sent during registration. Once verified, "
                    + "the user can log in to LimitGuard."
    )

    // Swagger: documents possible HTTP responses
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Email verified successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Missing, invalid or expired verification token"
            )
    })
    // Handles email verification using the token sent to the users email
    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        // Send the verification token to the service
        userService.verifyEmail(token);
        // Tell the user that their email was verified successfully
        return new ResponseEntity<>("Email verified successfully", HttpStatus.OK);
    }



    // Swagger: documents the login endpoint
    @Operation(
            summary = "Log in to LimitGuard",
            description = "Authenticates a registered user using their email "
                    + "and password. Returns a JWT access token when the "
                    + "credentials are valid and the account is verified."
    )

    // Swagger: explains the possible HTTP responses
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful. JWT token returned."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request body or missing required fields"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid email or password"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Email not verified or account deactivated"
            )
    })
    @PostMapping("/login")
    // Takes the login JSON and checks the validation rules
    public ResponseEntity<LoginResponse> loginUser(@Valid @RequestBody LoginRequest loginDetails) {
        // Send the login details to the service
        LoginResponse loginResponse = userService.loginUser(loginDetails);
        // Return the login response with status 200 OK
        return new ResponseEntity<>(loginResponse, HttpStatus.OK);
    }


    // Swagger: documents the forgot-password endpoint
    @Operation(
            summary = "Request a password reset",
            description = "Allows a user to request a password reset email. "
                    + "The email contains a token that can be used "
                    + "to set a new password."
    )

    // Swagger: documents possible HTTP responses
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Password reset request processed"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request body or missing email address"
            )
    })
    // handles password-reset requests
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest forgotPasswordDetails) {
        // create and email a password-reset token
        userService.forgotPassword(forgotPasswordDetails);
        return new ResponseEntity<>("Password reset email sent", HttpStatus.OK);
    }



    // Swagger: documents the reset-password endpoint
    @Operation(
            summary = "Reset user password",
            description = "Allows a user to set a new password using a "
                    + "password-reset token received by email. "
                    + "The new password must meet LimitGuard's password "
                    + "security requirements."
    )

    // Swagger: explains the possible HTTP responses
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Password reset successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request details, weak password, "
                            + "or invalid or expired reset token"
            )
    })
    // handles setting a new password using a reset token
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordDetails) {
        // validate the token and update the password
        userService.resetPassword(resetPasswordDetails);
        return new ResponseEntity<>("Password reset successfully", HttpStatus.OK);
    }

    // Swagger: documents this protected endpoint
    @Operation(
            summary = "Change current user password",
            description = "Allows an authenticated user to change their password."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid password change request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    // allows the currently logged-in user to change their password
    @PatchMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest changePasswordRequest) {

        // sends the password details to the service
        userService.changePassword(changePasswordRequest);

        return new ResponseEntity<>("Password changed successfully", HttpStatus.OK);
    }


    // Swagger: documents this protected endpoint
    @Operation(
            summary = "Get current user profile",
            description = "Returns the authenticated user profile."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    // allows the currently logged-in user to view their own profile
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        // gets the logged-in users profile from the service
        UserProfileResponse userProfile = userService.getMyProfile();
        return new ResponseEntity<>(userProfile, HttpStatus.OK);
    }


    // Swagger: documents this protected endpoint
    @Operation(
            summary = "Update current user profile",
            description = "Updates the authenticated user profile."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid profile update request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    // allows the currently logged-in user to update their own profile
    @PatchMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest updateProfileRequest) {

        // sends the updated profile information to the service
        UserProfileResponse updatedProfile =
                userService.updateMyProfile(updateProfileRequest);

        return new ResponseEntity<>(updatedProfile, HttpStatus.OK);
    }

    // Swagger: documents this protected endpoint
    @Operation(
            summary = "Upload current user profile picture",
            description = "Uploads or replaces the authenticated user profile picture."
    )

    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Profile picture updated successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid profile picture upload"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unable to save profile picture"
            )
    })
    // uploads or replaces the profile picture of the currently logged-in user
    @PatchMapping("/profile-picture")
    public ResponseEntity<UserProfileResponse> uploadProfilePicture(@RequestParam("image") MultipartFile image) throws IOException {
        UserProfileResponse updatedProfile = userService.uploadProfilePicture(image);
        return new ResponseEntity<>(updatedProfile, HttpStatus.OK);
    }


    // Swagger: documents this protected endpoint
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
    @PatchMapping("/{userId}/deactivate")
    // a security check that happens before the method is allowed to run
    // it checks if currently logged-in user have the ADMIN role?
    // If yes, Spring allows Java to enter deactivateUser()
    // If no, Spring stops there.
    @PreAuthorize("hasRole('ADMIN')")
    // allows an admin to deactivate another user account
    public ResponseEntity<String> deactivateUser(@PathVariable Long userId,
                                                 @Valid @RequestBody DeactivateUserRequest deactivateUserRequest) {

        // sends the user id and deactivation reason to the service
        userService.deactivateUser(userId, deactivateUserRequest);
        // returns a success message after the user is deactivated
        return new ResponseEntity<>("User deactivated successfully", HttpStatus.OK);
    }


    // Swagger: documents this protected endpoint
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
    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    // allows an admin to change another users role
    public ResponseEntity<String> updateUserRole(@PathVariable Long userId,
                                                 @Valid @RequestBody UpdateUserRoleRequest updateUserRoleRequest) {

        userService.updateUserRole(userId, updateUserRoleRequest);
        return new ResponseEntity<>("User role updated successfully", HttpStatus.OK);
    }
}
