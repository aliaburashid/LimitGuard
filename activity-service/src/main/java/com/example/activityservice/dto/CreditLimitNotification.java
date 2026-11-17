package com.example.activityservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Carries a facility limit change over Kafka.
// This DTO is used to receive the JSON message from Kafka
@Getter
@Setter
public class CreditLimitNotification {

    private Long id;
    private Long financialInstitutionId;
    private String eventType;
    private Long creditLimitId;
    private BigDecimal oldLimitAmount;
    private BigDecimal newLimitAmount;
}
