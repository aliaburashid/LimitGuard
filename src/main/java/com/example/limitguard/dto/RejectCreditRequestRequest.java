package com.example.limitguard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class RejectCreditRequestRequest {

    @Schema(
            description = "Reason provided by the checker for rejecting the credit request. Must not be blank",
            example = "Insufficient available credit headroom",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Rejection reason is required")
    private String reason;
}
