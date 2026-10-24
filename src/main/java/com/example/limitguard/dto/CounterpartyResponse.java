package com.example.limitguard.dto;

import com.example.limitguard.enums.CounterpartyStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@Setter
public class CounterpartyResponse {
    @Schema(
            description = "Unique identifier of the counterparty",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long id;

    @Schema(
            description = "Name of the counterparty",
            example = "ABC Trading Company",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String name;

    @Schema(
            description = "Current status of the counterparty",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private CounterpartyStatus status;

    @Schema(
            description = "Date and time when the counterparty was created",
            example = "2026-10-24T10:30:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime createdAt;

    @Schema(
            description = "Date and time when the counterparty was last updated",
            example = "2026-10-24T12:45:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime updatedAt;
}
