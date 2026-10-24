package com.example.limitguard.dto;

import com.example.limitguard.enums.FinancialInstitutionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@Setter
public class FinancialInstitutionResponse {

    @Schema(
            description = "Unique identifier of the financial institution",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long id;

    @Schema(
            description = "Name of the financial institution",
            example = "Bahrain National Bank",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String name;

    @Schema(
            description = "Current status of the financial institution",
            example = "ACTIVE",
            allowableValues = {"ACTIVE", "INACTIVE"},
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private FinancialInstitutionStatus status;

    @Schema(
            description = "Date and time when the financial institution was created",
            example = "2026-10-24T10:30:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime createdAt;

    @Schema(
            description = "Date and time when the financial institution was last updated",
            example = "2026-10-24T12:45:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime updatedAt;
}
