package com.example.limitguard.controller;

import com.example.limitguard.dto.CompanyRiskAssessmentResponse;
import com.example.limitguard.service.ParallelRiskAssessmentService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/corporate-groups")
@RequiredArgsConstructor
public class ParallelRiskAssessmentController {

    // service that runs ml risk predictions for multiple companies
    private final ParallelRiskAssessmentService parallelRiskAssessmentService;

    // only risk officers can request a corporate group risk assessment
    @PreAuthorize("hasRole('RISK_OFFICER')")
    @PostMapping("/{groupId}/risk-assessment")
    public ResponseEntity<List<CompanyRiskAssessmentResponse>> assessGroup(
            @PathVariable Long groupId) {

        // assess the companies in the group using the threadpool
        List<CompanyRiskAssessmentResponse> results =
                parallelRiskAssessmentService.assessGroup(groupId);

        // return the completed company assessments to the client
        return ResponseEntity.ok(results);
    }
}
