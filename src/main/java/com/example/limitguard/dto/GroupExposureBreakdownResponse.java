package com.example.limitguard.dto;

import java.math.BigDecimal;
import java.util.List;

// shared group capacity and the individual facilities that contribute to it
public record GroupExposureBreakdownResponse(
        BigDecimal limitAmount,
        BigDecimal usedAmount,
        BigDecimal reservedAmount,
        BigDecimal availableHeadroom,
        List<CreditExposureResponse> facilities
) {
}
