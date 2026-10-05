package com.example.limitguard.dto;

import com.example.limitguard.model.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRoleRequest {
    @NotNull(message = "Role is required")
    private UserRole role;
}
