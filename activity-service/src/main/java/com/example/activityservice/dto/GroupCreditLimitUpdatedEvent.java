package com.example.activityservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

// The group limit fields sent to the browser.
// This DTO is used to send the SSE notification to React
@Getter
@AllArgsConstructor
public class GroupCreditLimitUpdatedEvent {

    private final Long groupCreditLimitId;
    private final Long corporateGroupId;
    private final BigDecimal oldLimitAmount;
    private final BigDecimal newLimitAmount;
}
