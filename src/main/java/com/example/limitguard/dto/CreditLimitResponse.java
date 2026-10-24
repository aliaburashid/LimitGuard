package com.example.limitguard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@Setter
public class CreditLimitResponse {
    @Schema(
            description = "Unique identifier of the credit limit",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long id;

    @Schema(
            description = "Total approved credit limit amount for the counterparty",
            example = "1000000.00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private BigDecimal limitAmount;

    @Schema(
            description = "Amount of the credit limit currently used",
            example = "350000.00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private BigDecimal usedAmount;

    @Schema(
            description = "Amount of the credit limit currently reserved",
            example = "150000.00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private BigDecimal reservedAmount;

    @Schema(
            description = "Unique identifier of the financial institution that owns the credit limit",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long financialInstitutionId;

    @Schema(
            description = "Unique identifier of the counterparty associated with the credit limit",
            example = "2",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long counterpartyId;

    @Schema(
            description = "Date and time when the credit limit was created",
            example = "2026-10-24T10:30:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime createdAt;

    @Schema(
            description = "Date and time when the credit limit was last updated",
            example = "2026-10-24T12:45:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime updatedAt;
}
