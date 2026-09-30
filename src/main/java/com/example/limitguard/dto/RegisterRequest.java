package com.example.limitguard.dto;

// The information needed when a new user registers
// @NotBlank: is good for Strings because it rejects null, "", and "   "
// @NotNull: works well for financialInstitutionId because it's a Long, not a String
// @Email: checks that the email has a valid email format

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    // The user's first name is required
    @NotBlank(message = "First name is required")
    private String firstName;

    // The user's last name is required
    @NotBlank(message = "Last name is required")
    private String lastName;

    // The email is required and must be a valid email address
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    // The user's password before it is encrypted
    // The password is required
    @NotBlank(message = "Password is required")
    private String password;

    // The user must belong to a financial institution
    @NotNull(message = "Financial institution is required")
    private Long financialInstitutionId;
}
