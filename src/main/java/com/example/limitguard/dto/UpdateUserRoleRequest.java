package com.example.limitguard.dto;

import com.example.limitguard.enums.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class UpdateUserRoleRequest {
    @Schema(
            description = "New role to assign to the user. Must be a valid UserRole enum value",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Role is required")
    private UserRole role;
}
