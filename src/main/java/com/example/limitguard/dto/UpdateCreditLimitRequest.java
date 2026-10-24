package com.example.limitguard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateCreditLimitRequest {

    @Schema(
            description = "New total credit limit amount. Must be greater than zero",
            example = "1500000.00",
            minimum = "0",
            exclusiveMinimum = true,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Credit limit amount is required")
    @Positive(message = "Credit limit amount must be greater than zero")
    private BigDecimal limitAmount;
}
