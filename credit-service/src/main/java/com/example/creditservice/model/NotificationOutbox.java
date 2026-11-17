package com.example.creditservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Marks this class as a JPA entity.
// Each object represents one notification event stored in PostgreSQL.
@Entity

// Specifies the database table where notification events are stored.
@Table(name = "notification_outbox")

// Lombok automatically generates getters and setters for all attributes.
@Getter
@Setter

// Generates an empty constructor required by JPA.
@NoArgsConstructor
public class NotificationOutbox {

    // Primary key that uniquely identifies each notification event.
    @Id

    // PostgreSQL generates the ID when the event is saved.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identifies the bank that owns this notification.
    // Used to ensure notifications are delivered to the correct institution.
    @Column(nullable = false)
    private Long financialInstitutionId;

    // Identifies the credit request that triggered the notification.
    private Long creditRequestId;

    // Stores the ID of the user who originally submitted the credit request.
    // Notification Service can later use this ID to find the user's email.
    private Long requesterId;

    // IDs used by counterparty and credit limit notifications.
    private Long counterpartyId;

    private Long creditLimitId;

    private Long groupCreditLimitId;

    private Long corporateGroupId;

    // Store limit amounts with the same precision as the credit limit tables.
    @Column(precision = 19, scale = 2)
    private BigDecimal oldLimitAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal newLimitAmount;

    //-----------------EVENT INFORMATION----------------------------
    // Stores the new status for a credit request or counterparty.
    // Example: RESERVED, PENDING_APPROVAL, REJECTED, USED or EXPIRED.
    private String status;

    // Describes what type of event occurred.
    // Example: CREDIT_REQUEST_APPROVED or CREDIT_REQUEST_REJECTED.
    @Column(nullable = false)
    private String eventType;

    // Stores an optional explanation for the event.
    // For example, the reason a Risk Officer rejected a credit request.
    @Column(columnDefinition = "TEXT")
    private String reason;

    // Automatically records when the notification event was created.
    // Hibernate fills this value when the entity is first saved.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Records when the event was successfully published to Kafka.
    // NULL means the event has not been published yet.
    // This field starts as null and gets populated only after Kafka acknowledges the message
    // The relay knows which notifications still need publishing by looking for records where publishedAt is NULL
    private LocalDateTime publishedAt;
}
