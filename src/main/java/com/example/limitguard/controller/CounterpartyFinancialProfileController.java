package com.example.limitguard.controller;

import com.example.limitguard.dto.CounterpartyFinancialProfileRequest;
import com.example.limitguard.dto.CounterpartyFinancialProfileResponse;
import com.example.limitguard.service.CounterpartyFinancialProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/counterparties/{counterpartyId}/financial-profile")
public class CounterpartyFinancialProfileController {

    private final CounterpartyFinancialProfileService profileService;

    // creates or replaces the current financial profile
    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER')")
    public ResponseEntity<CounterpartyFinancialProfileResponse> saveProfile(
            @PathVariable Long counterpartyId,
            @Valid @RequestBody CounterpartyFinancialProfileRequest request) {
        return ResponseEntity.ok(profileService.saveProfile(counterpartyId, request));
    }

    // allows authorized staff to view the current financial profile
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER', 'RELATIONSHIP_MANAGER')")
    public ResponseEntity<CounterpartyFinancialProfileResponse> getProfile(
            @PathVariable Long counterpartyId) {
        return ResponseEntity.ok(profileService.getProfile(counterpartyId));
    }
}
