package com.example.creditservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

// Defines the fields sent to Kafka for a credit request notification.
@Getter
@AllArgsConstructor
public class CreditRequestNotification {

    private final Long id;
    private final Long financialInstitutionId;
    private final Long creditRequestId;
    private final Long requesterId;
    private final String status;
    private final String eventType;
    private final String reason;
    private final LocalDateTime createdAt;
    private final LocalDateTime publishedAt;
}
