package com.example.limitguard.controller;

import com.example.limitguard.dto.CreditExposureResponse;
import com.example.limitguard.dto.CreditFacilityResponse;
import com.example.limitguard.dto.CreditExposureSnapshotResponse;
import com.example.limitguard.dto.CreditLimitRequest;
import com.example.limitguard.dto.CreditLimitResponse;
import com.example.limitguard.dto.UpdateCreditLimitRequest;
import com.example.limitguard.documentation.CreditLimitApi;
import com.example.limitguard.service.CreditLimitService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-limits")
public class CreditLimitController implements CreditLimitApi {

    @Autowired
    private CreditLimitService creditLimitService;

    // creates a new credit limit
    @PostMapping
    @PreAuthorize("hasRole('RISK_OFFICER')")
    @Override
    public ResponseEntity<CreditLimitResponse> createCreditLimit(
            @Valid @RequestBody CreditLimitRequest creditLimitRequest) {
        // sends the request information to the service
        CreditLimitResponse creditLimitResponse = creditLimitService.createCreditLimit(creditLimitRequest);
        // returns the created credit limit with 201 Created
        return new ResponseEntity<>(creditLimitResponse, HttpStatus.CREATED);
    }

    // lists individual facilities using the same role access as the existing exposure lookup
    @GetMapping
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER', 'RISK_OFFICER', 'ADMIN')")
    @Override
    public ResponseEntity<Page<CreditFacilityResponse>> getFacilityDirectory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        return ResponseEntity.ok(creditLimitService.getFacilityDirectory(pageable));
    }

    // gets a credit limits exposure and available headroom
    @GetMapping("/{creditLimitId}/exposure")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER', 'RISK_OFFICER', 'ADMIN')")
    @Override
    public ResponseEntity<CreditExposureResponse> getCreditExposure(@PathVariable Long creditLimitId) {
        CreditExposureResponse creditExposureResponse = creditLimitService.getCreditExposure(creditLimitId);
        return new ResponseEntity<>(creditExposureResponse, HttpStatus.OK);
    }

    // gets the dated individual facility balances for the Relationship Manager overview
    @GetMapping("/exposure-history")
    @PreAuthorize("hasRole('RELATIONSHIP_MANAGER')")
    @Override
    public ResponseEntity<List<CreditExposureSnapshotResponse>> getExposureHistory(
            @RequestParam(defaultValue = "false") boolean includeCurrent) {
        return ResponseEntity.ok(creditLimitService.getExposureHistory(includeCurrent));
    }

    // updates an existing credit limit
    @PutMapping("/{creditLimitId}")
    @PreAuthorize("hasRole('RISK_OFFICER')")
    @Override
    public ResponseEntity<CreditLimitResponse> updateCreditLimit(
            @PathVariable Long creditLimitId,
            @Valid @RequestBody UpdateCreditLimitRequest updateCreditLimitRequest) {
        CreditLimitResponse creditLimitResponse =
                creditLimitService.updateCreditLimit(creditLimitId, updateCreditLimitRequest);
        return new ResponseEntity<>(creditLimitResponse, HttpStatus.OK);
    }
}
