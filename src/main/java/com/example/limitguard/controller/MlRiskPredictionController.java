package com.example.limitguard.controller;

import com.example.limitguard.dto.MlRiskPredictionResponse;
import com.example.limitguard.service.MlRiskPredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/counterparties")
@RequiredArgsConstructor
public class MlRiskPredictionController {

    private final MlRiskPredictionService mlRiskPredictionService;

    // only risk officers can request an ml risk prediction
    @PreAuthorize("hasRole('RISK_OFFICER')")
    @PostMapping("/{counterpartyId}/risk-prediction")
    public ResponseEntity<MlRiskPredictionResponse> predictRisk(
            @PathVariable Long counterpartyId) {

        // get the prediction from the python ml service
        MlRiskPredictionResponse prediction =
                mlRiskPredictionService.predictRisk(counterpartyId);

        return ResponseEntity.ok(prediction);
    }
}
