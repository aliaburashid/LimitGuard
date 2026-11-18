package com.example.activityservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Carries a group limit change over Kafka.
// This DTO is used to receive the JSON message from Kafka
@Getter
@Setter
public class GroupCreditLimitNotification {

    private Long id;
    private Long financialInstitutionId;
    private String eventType;
    private Long groupCreditLimitId;
    private Long corporateGroupId;
    private BigDecimal oldLimitAmount;
    private BigDecimal newLimitAmount;
}
