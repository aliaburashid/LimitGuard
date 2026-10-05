package com.example.limitguard.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "credit_limits",
        // Only one credit limit can exist for the same financial institution and counterparty combination
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"financial_institution_id", "counterparty_id"})
        }
)
public class CreditLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // for money amounts, BigDecimal gives us controlled decimal precision
    // maximum credit exposure allowed
    // precision = 19, scale = 2: mean we store financial amounts with 2 decimal places
    @Column(name = "limit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal limitAmount;

    // amount that has already been used
    @Column(name = "used_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal usedAmount = BigDecimal.ZERO;

    // amount currently reserved for approved credit requests
    @Column(name = "reserved_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal reservedAmount = BigDecimal.ZERO;

    // financial institution that owns this credit limit
    @ManyToOne
    @JoinColumn(name = "financial_institution_id", nullable = false)
    private FinancialInstitution financialInstitution;

    // counterparty that this credit limit applies to
    @ManyToOne
    @JoinColumn(name = "counterparty_id", nullable = false)
    private Counterparty counterparty;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}