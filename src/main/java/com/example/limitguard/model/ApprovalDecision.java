package com.example.limitguard.model;

import com.example.limitguard.enums.ApprovalDecisionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "approval_decisions",
        // makes sure each credit request can only have one final approval decision
        uniqueConstraints = {
                @UniqueConstraint(name = "unique_credit_request_decision", columnNames = "credit_request_id")
        }
)
public class ApprovalDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // stores whether the Risk Officer approved or rejected the request
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalDecisionType decision;

    // stores the reason for the decision, mainly used when rejecting a request
    @Column
    private String reason;

    // one credit request can only have one final approval decision
    @OneToOne
    @JoinColumn(name = "credit_request_id", nullable = false)
    private CreditRequest creditRequest;

    // one Risk Officer can make decisions on many credit requests
    @ManyToOne
    @JoinColumn(name = "decided_by_id", nullable = false)
    private User decidedBy;

    // records when the decision was made
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}