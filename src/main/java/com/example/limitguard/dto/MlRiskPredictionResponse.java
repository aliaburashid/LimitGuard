package com.example.limitguard.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class MlRiskPredictionResponse {

    // probability returned by the pretrained model
    private BigDecimal probability;

    // low medium or high risk classification
    private String riskLevel;

    // identifies the pretrained model version
    private String modelVersion;
}
