package com.example.limitguard.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "group_credit_limits")
public class GroupCreditLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // stores the corporate group receiving the credit limit
    @ManyToOne
    @JoinColumn(name = "corporate_group_id", nullable = false)
    private CorporateGroup corporateGroup;

    // stores the bank that owns this group credit limit
    @ManyToOne
    @JoinColumn(name = "financial_institution_id", nullable = false)
    private FinancialInstitution financialInstitution;

    // stores the approved credit limit amount
    // stores monetary values with two decimal places
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // automatically stores when the credit limit was created
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // automatically stores when the credit limit was last updated
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
