package com.example.limitguard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@AllArgsConstructor
@Getter
@Setter
public class CreditExposureResponse {

    @Schema(
            description = "Unique identifier of the credit limit",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long creditLimitId;

    @Schema(
            description = "Unique identifier of the counterparty associated with the credit limit",
            example = "2",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long counterpartyId;

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
            description = "Amount of the credit limit currently reserved for pending requests",
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
}
