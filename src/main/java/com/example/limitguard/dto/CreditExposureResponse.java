package com.example.limitguard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@AllArgsConstructor
@Getter
@Setter
public class CreditExposureResponse {

    private Long creditLimitId;
    private Long counterpartyId;
    private BigDecimal limitAmount;
    private BigDecimal usedAmount;
    private BigDecimal reservedAmount;
    private BigDecimal availableHeadroom;
}