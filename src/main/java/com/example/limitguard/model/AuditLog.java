package com.example.limitguard.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor


@Getter
@Setter
@Entity
@Table (name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // the action that happened, for example USER_DEACTIVATED
    @Column(nullable = false)
    private String action;

    // the type of record affected, for example USER
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    // the id of the record affected
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    // extra information about what happened
    @Column
    private String details;

    // the user who performed the action
    // in the database this will be stored as actor_id
    // Reservation expires automatically -> actor = null
    @ManyToOne
    @JoinColumn(name = "actor_id")
    private User actor;

    // automatically stores when the audit log was created
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
