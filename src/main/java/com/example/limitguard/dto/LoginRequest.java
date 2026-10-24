
package com.example.limitguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

// The information needed when a user logs in

@Getter
@Setter
public class LoginRequest {

    // The email used to find the user's account
    @Schema(
            description = "Email address registered with the user's LimitGuard account",
            example = "sara.ahmed@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    // The password used to authenticate the user
    @Schema(
            description = "Password used to authenticate the user",
            example = "SecurePass123!",
            format = "password",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Password is required")
    private String password;
}
