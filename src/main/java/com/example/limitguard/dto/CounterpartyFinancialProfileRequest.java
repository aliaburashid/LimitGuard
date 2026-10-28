package com.example.limitguard.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CounterpartyFinancialProfileRequest {

    // profitability percentages can be negative when a company makes a loss
    @NotNull
    private BigDecimal returnOnAssets;

    @NotNull
    private BigDecimal profitMargin;

    // turnover and debt ratios must not be negative for this prototype
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal fixedAssetTurnover;

    // the model uses debt divided by total capital rather than debt to equity
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal debtToTotalCapitalPercent;

    // negative interest coverage may occur when ebitda is negative
    @NotNull
    private BigDecimal interestCoverage;

    // negative debt to ebitda may occur when ebitda is negative
    @NotNull
    private BigDecimal totalDebtToEbitda;

    // financial statements must have a known reporting date
    @NotNull
    @PastOrPresent
    private LocalDate financialYearEnd;
}
