package com.example.limitguard.dto;

import com.example.limitguard.enums.CreditRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

// REAL TIME NOTIFICATION
// Data sent to clients when a credit request changes status
@Getter
@AllArgsConstructor
public class CreditRequestStatusEvent {

    // Which request changed?
    @Schema(
            description = "Unique identifier of the credit request whose status has changed",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long creditRequestId;

    // What is its new status?
    @Schema(
            description = "New status of the credit request after the status change",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private CreditRequestStatus status;
}
