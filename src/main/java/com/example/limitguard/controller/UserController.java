package com.example.limitguard.controller;

import com.example.limitguard.dto.*;
import com.example.limitguard.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


// tells Spring that this class contains API endpoints
@RestController
@RequestMapping("/api/auth/users")
public class UserController {

    // Gives the controller access to the user service
    @Autowired
    private UserService userService;

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


    // Handles email verification using the token sent to the users email
    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        // Send the verification token to the service
        userService.verifyEmail(token);
        // Tell the user that their email was verified successfully
        return new ResponseEntity<>("Email verified successfully", HttpStatus.OK);
    }

    @PostMapping("/login")
    // Takes the login JSON and checks the validation rules
    public ResponseEntity<LoginResponse> loginUser(@Valid @RequestBody LoginRequest loginDetails) {
        // Send the login details to the service
        LoginResponse loginResponse = userService.loginUser(loginDetails);
        // Return the login response with status 200 OK
        return new ResponseEntity<>(loginResponse, HttpStatus.OK);
    }

    // handles password-reset requests
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest forgotPasswordDetails) {
        // create and email a password-reset token
        userService.forgotPassword(forgotPasswordDetails);
        return new ResponseEntity<>("Password reset email sent", HttpStatus.OK);
    }


    // handles setting a new password using a reset token
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordDetails) {
        // validate the token and update the password
        userService.resetPassword(resetPasswordDetails);
        return new ResponseEntity<>("Password reset successfully", HttpStatus.OK);
    }

    // allows the currently logged-in user to change their password
    @PatchMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest changePasswordRequest) {

        // sends the password details to the service
        userService.changePassword(changePasswordRequest);

        return new ResponseEntity<>("Password changed successfully", HttpStatus.OK);
    }


    // allows the currently logged-in user to view their own profile
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        // gets the logged-in users profile from the service
        UserProfileResponse userProfile = userService.getMyProfile();
        return new ResponseEntity<>(userProfile, HttpStatus.OK);
    }


    // allows the currently logged-in user to update their own profile
    @PatchMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest updateProfileRequest) {

        // sends the updated profile information to the service
        UserProfileResponse updatedProfile =
                userService.updateMyProfile(updateProfileRequest);

        return new ResponseEntity<>(updatedProfile, HttpStatus.OK);
    }
}
