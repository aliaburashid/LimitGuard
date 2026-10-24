
package com.example.limitguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

// Information needed to request a password reset
@Getter
@Setter
public class ForgotPasswordRequest {

    // The email belonging to the account
    @Schema(
            description = "Email address associated with the account requesting a password reset",
            example = "sara.ahmed@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;
}
