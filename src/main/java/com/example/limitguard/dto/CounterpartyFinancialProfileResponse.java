package com.example.limitguard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CounterpartyFinancialProfileResponse {
    private Long id;
    private Long counterpartyId;
    private String counterpartyName;
    private BigDecimal returnOnAssets;
    private BigDecimal profitMargin;
    private BigDecimal fixedAssetTurnover;
    private BigDecimal debtToTotalCapitalPercent;
    private BigDecimal interestCoverage;
    private BigDecimal totalDebtToEbitda;
    private LocalDate financialYearEnd;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
