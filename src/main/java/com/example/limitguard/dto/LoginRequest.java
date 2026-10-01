package com.example.limitguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// The information needed when a user logs in

@Getter
@Setter
public class LoginRequest {

    // The email used to find the user's account
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    // The password used to authenticate the user
    @NotBlank(message = "Password is required")
    private String password;
}