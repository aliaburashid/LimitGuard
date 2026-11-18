package com.example.activityservice.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

// Stores the notification received from Credit Service through Kafka.
@Getter
@Setter
public class CreditRequestNotification {

    // Unique ID of the notification in the Credit Service outbox.
    private Long id;

    // ID of the bank this notification belongs to.
    private Long financialInstitutionId;

    // ID of the credit request that was updated.
    private Long creditRequestId;

    // ID of the user who originally created the credit request.
    private Long requesterId;

    // Current status of the credit request (e.g. APPROVED or REJECTED).
    private String status;

    // Type of event that occurred.
    private String eventType;

    // Reason associated with the status change.
    private String reason;

    // Date and time the notification was created.
    private LocalDateTime createdAt;

    // Date and time the notification was published to Kafka.
    private LocalDateTime publishedAt;
}
