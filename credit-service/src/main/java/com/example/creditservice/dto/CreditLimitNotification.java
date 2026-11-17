package com.example.creditservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

// Carries a facility limit change over Kafka.
// This DTO is used to send a notification to Kafka.
@Getter
@AllArgsConstructor
public class CreditLimitNotification {

    private final Long id;
    private final Long financialInstitutionId;
    private final String eventType;
    private final Long creditLimitId;
    private final BigDecimal oldLimitAmount;
    private final BigDecimal newLimitAmount;
}
