package com.example.limitguard.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "counterparty_financial_profiles")
public class CounterpartyFinancialProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // each counterparty has one current financial profile
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counterparty_id", nullable = false, unique = true)
    private Counterparty counterparty;

    // return on assets as a percentage such as 8 for 8 percent
    @Column(name = "return_on_assets", nullable = false, precision = 18, scale = 6)
    private BigDecimal returnOnAssets;

    // net income margin as a percentage such as 12 for 12 percent
    @Column(name = "profit_margin", nullable = false, precision = 18, scale = 6)
    private BigDecimal profitMargin;

    // fixed asset turnover as a multiple such as 1.5
    @Column(name = "fixed_asset_turnover", nullable = false, precision = 18, scale = 6)
    private BigDecimal fixedAssetTurnover;

    // total debt divided by total capital as a percentage
    // this is not the same as debt divided by equity
    @Column(name = "debt_to_total_capital_percent", nullable = false, precision = 18, scale = 6)
    private BigDecimal debtToTotalCapitalPercent;

    // ebitda divided by interest expense as a multiple
    @Column(name = "interest_coverage", nullable = false, precision = 18, scale = 6)
    private BigDecimal interestCoverage;

    // total debt divided by ebitda as a multiple
    @Column(name = "total_debt_to_ebitda", nullable = false, precision = 18, scale = 6)
    private BigDecimal totalDebtToEbitda;

    // records when these financial figures were measured
    @Column(name = "financial_year_end", nullable = false)
    private java.time.LocalDate financialYearEnd;

    // timestamps are managed automatically by hibernate
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
