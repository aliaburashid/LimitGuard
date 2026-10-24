package com.example.limitguard.dto;

import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.enums.CounterpartyStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CreditRequestReviewResponse {

    // credit request information
    @Schema(
            description = "Unique identifier of the credit request being reviewed",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long creditRequestId;

    @Schema(
            description = "Amount of credit capacity requested",
            example = "250000.00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private BigDecimal requestedAmount;

    @Schema(
            description = "Current status of the credit request",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private CreditRequestStatus status;

    @Schema(
            description = "Date and time when the credit request was created",
            example = "2026-10-24T10:30:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime createdAt;

    // requester information
    @Schema(
            description = "Unique identifier of the user who submitted the credit request",
            example = "3",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long requesterId;

    @Schema(
            description = "Name of the user who submitted the credit request",
            example = "Sara Ahmed",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String requesterName;

    // counterparty information
    @Schema(
            description = "Unique identifier of the counterparty associated with the credit request",
            example = "2",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long counterpartyId;

    @Schema(
            description = "Name of the counterparty associated with the credit request",
            example = "ABC Trading Company",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String counterpartyName;

    // credit limit and current exposure information
    @Schema(
            description = "Unique identifier of the credit limit associated with the request",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long creditLimitId;

    @Schema(
            description = "Total approved credit limit amount",
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
            description = "Remaining available credit capacity after subtracting used and reserved amounts from the total credit limit",
            example = "500000.00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private BigDecimal availableHeadroom;

    @Schema(
            description = "Date and time when the credit request expires",
            example = "2026-10-31T10:30:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime expiresAt;

    @Schema(
            description = "Current status of the counterparty associated with the credit request",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private CounterpartyStatus counterpartyStatus;

    @Schema(
            description = "Unique identifier of the financial institution associated with the credit request",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long financialInstitutionId;

    @Schema(
            description = "Name of the financial institution associated with the credit request",
            example = "Bahrain National Bank",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String financialInstitutionName;
}
