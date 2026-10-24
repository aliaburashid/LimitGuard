
package com.example.limitguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class ChangePasswordRequest {
    @Schema(
            description = "Current password of the authenticated user",
            example = "CurrentPass123!",
            format = "password",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    // new password cannot be empty
    @Schema(
            description = "New password containing at least 8 characters, "
                    + "including uppercase and lowercase letters, "
                    + "a number, and a special character",
            example = "NewSecurePass123!",
            format = "password",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "New password is required")
    // new password must contain at least 8 characters
    @Size(min = 8, message = "Password must be at least 8 characters")
    // checks that the new password contains uppercase, lowercase, number and special character
    @Pattern(
            // regex defines the password strength requirements
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
            message = "Password must contain an uppercase letter, lowercase letter, number and special character"
    )
    private String newPassword;
}
