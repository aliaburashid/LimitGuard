package com.example.activityservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

// The facility limit fields sent to the browser.
// This DTO is used to send the SSE notification to React
@Getter
@AllArgsConstructor
public class CreditLimitUpdatedEvent {

    private final Long creditLimitId;
    private final BigDecimal oldLimitAmount;
    private final BigDecimal newLimitAmount;
}
