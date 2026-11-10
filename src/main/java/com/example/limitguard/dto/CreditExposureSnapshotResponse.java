package com.example.limitguard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

// one dated observation of individual facility exposure
public record CreditExposureSnapshotResponse(
        Instant capturedAt,
        BigDecimal limitAmount,
        BigDecimal usedAmount,
        BigDecimal reservedAmount,
        boolean synthetic,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean current
) {
    public CreditExposureSnapshotResponse(Instant capturedAt, BigDecimal limitAmount, BigDecimal usedAmount,
                                          BigDecimal reservedAmount, boolean synthetic) {
        this(capturedAt, limitAmount, usedAmount, reservedAmount, synthetic, false);
    }
}
