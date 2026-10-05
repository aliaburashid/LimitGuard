package com.example.limitguard.controller;

import com.example.limitguard.dto.CreditExposureResponse;
import com.example.limitguard.dto.CreditLimitRequest;
import com.example.limitguard.dto.CreditLimitResponse;
import com.example.limitguard.service.CreditLimitService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/credit-limits")
public class CreditLimitController {

    @Autowired
    private CreditLimitService creditLimitService;

    // creates a new credit limit
    @PostMapping
    @PreAuthorize("hasRole('RISK_OFFICER')")
    public ResponseEntity<CreditLimitResponse> createCreditLimit(
            @Valid @RequestBody CreditLimitRequest creditLimitRequest) {
        // sends the request information to the service
        CreditLimitResponse creditLimitResponse = creditLimitService.createCreditLimit(creditLimitRequest);
        // returns the created credit limit with 201 Created
        return new ResponseEntity<>(creditLimitResponse, HttpStatus.CREATED);
    }

    // gets a credit limits exposure and available headroom
    @GetMapping("/{creditLimitId}/exposure")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER', 'RISK_OFFICER', 'ADMIN')")
    public ResponseEntity<CreditExposureResponse> getCreditExposure(@PathVariable Long creditLimitId) {
        CreditExposureResponse creditExposureResponse = creditLimitService.getCreditExposure(creditLimitId);
        return new ResponseEntity<>(creditExposureResponse, HttpStatus.OK);
    }
}