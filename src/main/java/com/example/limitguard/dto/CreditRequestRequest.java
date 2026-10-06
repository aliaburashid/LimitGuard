package com.example.limitguard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreditRequestRequest {

    // credit limit that the request is made against
    @NotNull(message = "Credit limit ID is required")
    private Long creditLimitId;

    // amount of credit capacity being requested
    @NotNull(message = "Requested amount is required")
    @Positive(message = "Requested amount must be greater than zero")
    private BigDecimal amount;
}