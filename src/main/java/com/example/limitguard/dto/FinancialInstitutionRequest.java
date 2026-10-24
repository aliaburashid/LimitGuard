package com.example.limitguard.dto;

import com.example.limitguard.enums.FinancialInstitutionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class FinancialInstitutionRequest {
    // the financial institution must have a name
    @Schema(
            description = "Name of the financial institution",
            example = "Bahrain National Bank",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Financial institution name is required")
    private String name;

    // the status must be ACTIVE or INACTIVE
    @Schema(
            description = "Status of the financial institution. Must be ACTIVE or INACTIVE",
            example = "ACTIVE",
            allowableValues = {"ACTIVE", "INACTIVE"},
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Financial institution status is required")
    private FinancialInstitutionStatus status;
}
