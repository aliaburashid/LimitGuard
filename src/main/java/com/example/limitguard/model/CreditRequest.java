package com.example.limitguard.model;

import com.example.limitguard.enums.CreditRequestStatus;
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
@Table(name = "credit_requests")
public class CreditRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // amount of credit capacity being requested
    // precision = 19, scale = 2: mean we store financial amounts with 2 decimal places
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // current stage of the credit request
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreditRequestStatus status;

    // when a reservation should expire
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // user who submitted the request
    // One Relationship Manager can submit many credit requests,
    // but each credit request has one requester.
    @ManyToOne
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    // credit limit that the request is made against
    // One credit limit can have many requests against it,
    // but each request belongs to one credit limit.
    @ManyToOne
    @JoinColumn(name = "credit_limit_id", nullable = false)
    private CreditLimit creditLimit;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}