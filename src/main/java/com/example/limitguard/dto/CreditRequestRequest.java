package com.example.limitguard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Getter
@Setter
public class CreditRequestRequest {

    // credit limit that the request is made against
    @Schema(
            description = "Unique identifier of the credit limit against which the credit request is submitted",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Credit limit ID is required")
    private Long creditLimitId;

    // amount of credit capacity being requested
    @Schema(
            description = "Amount of credit capacity being requested. Must be greater than zero",
            example = "250000.00",
            minimum = "0",
            exclusiveMinimum = true,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Requested amount is required")
    @Positive(message = "Requested amount must be greater than zero")
    private BigDecimal amount;
}
