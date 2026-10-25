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
    private Long id;
    private String name;
    private FinancialInstitutionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
