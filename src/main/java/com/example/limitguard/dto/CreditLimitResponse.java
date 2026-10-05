package com.example.limitguard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@Setter
public class CreditLimitResponse {
    private Long id;
    private BigDecimal limitAmount;
    private BigDecimal usedAmount;
    private BigDecimal reservedAmount;
    private Long financialInstitutionId;
    private Long counterpartyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}