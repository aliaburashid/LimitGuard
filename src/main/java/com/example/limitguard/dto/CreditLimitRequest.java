package com.example.limitguard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreditLimitRequest {

    // financial institution that owns the credit limit
    @NotNull(message = "Financial institution ID is required")
    private Long financialInstitutionId;

    // counterparty that the limit applies to
    @NotNull(message = "Counterparty ID is required")
    private Long counterpartyId;

    // credit limit must be greater than zero
    @NotNull(message = "Credit limit amount is required")
    @Positive(message = "Credit limit amount must be greater than zero")
    private BigDecimal limitAmount;
}