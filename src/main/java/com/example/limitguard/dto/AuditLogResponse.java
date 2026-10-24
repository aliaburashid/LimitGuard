package com.example.limitguard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@Setter
public class AuditLogResponse {

    @Schema(
            description = "Unique identifier of the audit log entry",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long id;

    @Schema(
            description = "Action recorded in the audit log",
            example = "CREDIT_REQUEST_APPROVED",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String action;

    @Schema(
            description = "Type of entity affected by the recorded action",
            example = "CreditRequest",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String entityType;

    @Schema(
            description = "Unique identifier of the entity affected by the recorded action",
            example = "5",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long entityId;

    @Schema(
            description = "Additional information describing the recorded action",
            example = "Credit request approved by checker",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String details;

    @Schema(
            description = "Unique identifier of the user who performed the action",
            example = "3",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long actorId;

    @Schema(
            description = "Name of the user who performed the action",
            example = "Sara Ahmed",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String actorName;

    @Schema(
            description = "Date and time when the audit log entry was created",
            example = "2026-10-24T10:30:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime createdAt;
}
