package com.example.limitguard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyRiskAssessmentResponse {

    // the id of the company being assessed
    private Long counterpartyId;

    // the name of the company being assessed
    private String counterpartyName;

    // the probability returned by the ml model
    private BigDecimal probability;

    // the risk level returned by the ml model
    private String riskLevel;

    // tells us if the assessment completed successfully
    private boolean successful;

    // stores an error message if the assessment fails
    private String errorMessage;
}