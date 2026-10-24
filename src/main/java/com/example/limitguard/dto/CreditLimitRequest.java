package com.example.limitguard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Getter
@Setter
public class CreditLimitRequest {

    // financial institution that owns the credit limit
    @Schema(
            description = "Unique identifier of the financial institution that owns the credit limit",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Financial institution ID is required")
    private Long financialInstitutionId;

    // counterparty that the limit applies to
    @Schema(
            description = "Unique identifier of the counterparty to which the credit limit applies",
            example = "2",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Counterparty ID is required")
    private Long counterpartyId;

    // credit limit must be greater than zero
    @Schema(
            description = "Credit limit amount allocated to the counterparty. Must be greater than zero",
            example = "1000000.00",
            minimum = "0",
            // tells that 0 itself is not allowed
            exclusiveMinimum = true,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Credit limit amount is required")
    @Positive(message = "Credit limit amount must be greater than zero")
    private BigDecimal limitAmount;
}
