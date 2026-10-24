package com.example.limitguard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class DeactivateUserRequest {
    // reason provided by the admin for deactivating the account
    @Schema(
            description = "Reason provided by the administrator for deactivating the user account. Must not be blank",
            example = "User account deactivated following an internal compliance review",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Deactivation reason is required")
    private String reason;
}
