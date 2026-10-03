package com.example.limitguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Information needed to request a password reset
@Getter
@Setter
public class ForgotPasswordRequest {

    // The email belonging to the account
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;
}
