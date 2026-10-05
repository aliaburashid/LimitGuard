package com.example.limitguard.dto;

import com.example.limitguard.enums.FinancialInstitutionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FinancialInstitutionRequest {
    // the financial institution must have a name
    @NotBlank(message = "Financial institution name is required")
    private String name;

    // the status must be ACTIVE or INACTIVE
    @NotNull(message = "Financial institution status is required")
    private FinancialInstitutionStatus status;
}
