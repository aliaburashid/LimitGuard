package com.example.limitguard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Information needed to reset a forgotten password
@Getter
@Setter
public class ResetPasswordRequest {
    // The password-reset token sent to the users email
    @NotBlank(message = "Reset token is required")
    private String token;

    // The new password the user wants to use
    @NotBlank(message = "New password is required")
    private String newPassword;
}
