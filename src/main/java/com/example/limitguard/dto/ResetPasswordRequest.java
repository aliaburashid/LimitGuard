package com.example.limitguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Information needed to reset a forgotten password
@Getter
@Setter
public class ResetPasswordRequest {
    // The password-reset token sent to the users email
    @NotBlank(message = "Reset token is required")
    private String token;

    // the new password the user wants to use
    // new password cannot be empty
    @NotBlank(message = "New password is required")
    // new password must contain at least 8 characters
    @Size(min = 8, message = "Password must be at least 8 characters")
    // checks that the password contains uppercase, lowercase, number and special character
    @Pattern(
            // regex defines the password strength requirements
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
            message = "Password must contain an uppercase letter, lowercase letter, number and special character"
    )
    private String newPassword;
}
