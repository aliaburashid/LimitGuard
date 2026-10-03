package com.example.limitguard.dto;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    private String firstName;
    private String lastName;

    // the user can update their email, but it must be a valid email address
    @Email(message = "Email must be valid")
    private String email;
}
