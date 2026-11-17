package com.example.authservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "audit_logs", schema = "public")
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Store IDs as recorded at the time of the action.
    @Column
    private Long actorId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column
    private Long financialInstitutionId;

    @Column(columnDefinition = "text")
    private String details;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AuthAuditLog(Long actorId, String action, String entityType, Long entityId,
                        Long financialInstitutionId, String details) {
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.financialInstitutionId = financialInstitutionId;
        this.details = details;
    }
}
