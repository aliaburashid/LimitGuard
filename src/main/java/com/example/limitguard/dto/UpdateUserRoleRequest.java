package com.example.limitguard.dto;

import com.example.limitguard.enums.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class UpdateUserRoleRequest {
    
    @NotNull(message = "Role is required")
    private UserRole role;
}
