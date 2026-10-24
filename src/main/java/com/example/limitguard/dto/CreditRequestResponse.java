package com.example.limitguard.dto;

import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.enums.CounterpartyStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@Setter
public class CreditRequestResponse {

    @Schema(
            description = "Unique identifier of the credit request",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long id;

    @Schema(
            description = "Amount of credit capacity requested",
            example = "250000.00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private BigDecimal amount;

    @Schema(
            description = "Current status of the credit request",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private CreditRequestStatus status;

    @Schema(
            description = "Unique identifier of the credit limit associated with this request",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long creditLimitId;

    @Schema(
            description = "Unique identifier of the counterparty associated with this request",
            example = "2",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long counterpartyId;

    @Schema(
            description = "Unique identifier of the user who submitted the credit request",
            example = "3",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long requesterId;

    @Schema(
            description = "Date and time when the credit request expires",
            example = "2026-10-31T10:30:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime expiresAt;

    @Schema(
            description = "Date and time when the credit request was created",
            example = "2026-10-24T10:30:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime createdAt;

    @Schema(
            description = "Date and time when the credit request was last updated",
            example = "2026-10-24T12:45:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime updatedAt;

    @Schema(
            description = "Name of the counterparty associated with the credit request",
            example = "ABC Trading Company",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String counterpartyName;

    @Schema(
            description = "Current status of the counterparty associated with the credit request",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private CounterpartyStatus counterpartyStatus;

    @Schema(
            description = "Name of the user who submitted the credit request",
            example = "Sara Ahmed",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String requesterName;

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
