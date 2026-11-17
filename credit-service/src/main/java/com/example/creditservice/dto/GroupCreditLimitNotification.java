package com.example.creditservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

// Carries a group limit change over Kafka.
// This DTO is used to send a notification to Kafka.
@Getter
@AllArgsConstructor
public class GroupCreditLimitNotification {

    private final Long id;
    private final Long financialInstitutionId;
    private final String eventType;
    private final Long groupCreditLimitId;
    private final Long corporateGroupId;
    private final BigDecimal oldLimitAmount;
    private final BigDecimal newLimitAmount;
}
