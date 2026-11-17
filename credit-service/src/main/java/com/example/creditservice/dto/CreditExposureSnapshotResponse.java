package com.example.creditservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

// DTO representing a snapshot of a credit facility's exposure at a specific time.
@Data // Generates getters, setters, equals, hashCode and toString.
@NoArgsConstructor // Creates an empty constructor for JSON deserialization.
@AllArgsConstructor // Creates a constructor accepting all six fields.
public class CreditExposureSnapshotResponse {

    // Date and time when the exposure snapshot was recorded.
    private Instant capturedAt;

    // Maximum approved credit amount.
    private BigDecimal limitAmount;

    // Amount of credit currently being used.
    private BigDecimal usedAmount;

    // Amount reserved for pending credit requests.
    private BigDecimal reservedAmount;

    // Indicates whether this snapshot contains generated rather than recorded data.
    private boolean synthetic;

    // Excludes false values from JSON, preserving the original record's behavior.
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean current;

    // Keeps the original five-argument constructor.
    // When current is not provided, it defaults to false.
    public CreditExposureSnapshotResponse(
            Instant capturedAt,
            BigDecimal limitAmount,
            BigDecimal usedAmount,
            BigDecimal reservedAmount,
            boolean synthetic) {

        this(capturedAt, limitAmount, usedAmount, reservedAmount, synthetic, false);
    }
}
