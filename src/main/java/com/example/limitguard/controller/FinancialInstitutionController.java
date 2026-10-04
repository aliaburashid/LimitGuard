package com.example.limitguard.controller;

import com.example.limitguard.dto.FinancialInstitutionRequest;
import com.example.limitguard.dto.FinancialInstitutionResponse;
import com.example.limitguard.service.FinancialInstitutionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/financial-institutions")
public class FinancialInstitutionController {

    // gives this controller access to the FI service
    @Autowired
    private FinancialInstitutionService financialInstitutionService;

    @PostMapping
    // only a logged-in ADMIN can create a financial institution
    @PreAuthorize("hasRole('ADMIN')")
    // creates a new financial institution
    public ResponseEntity<FinancialInstitutionResponse> createFinancialInstitution (
            @Valid @RequestBody FinancialInstitutionRequest financialInstitutionRequest) {

        // sends the request information to the service to create the institution
        FinancialInstitutionResponse financialInstitutionResponse =
                financialInstitutionService.createFinancialInstitution(
                        financialInstitutionRequest
                );

        // returns the created institution with 201 Created
        return new ResponseEntity<>(financialInstitutionResponse, HttpStatus.CREATED);
    }


    @PutMapping("/{institutionId}")
    // only a logged-in ADMIN can update a financial institution
    @PreAuthorize("hasRole('ADMIN')")
    // updates an existing financial institution
    public ResponseEntity<FinancialInstitutionResponse> updateFinancialInstitution(
            // gets the institution ID from the URL
            @PathVariable Long institutionId,
            // gets and validates the new institution information from the request body
            @Valid @RequestBody FinancialInstitutionRequest financialInstitutionRequest) {

        // sends the ID and new information to the service to update the institution
        FinancialInstitutionResponse financialInstitutionResponse =
                financialInstitutionService.updateFinancialInstitution(
                        institutionId,
                        financialInstitutionRequest
                );

        // returns the updated institution with 200 OK
        return new ResponseEntity<>(financialInstitutionResponse, HttpStatus.OK);
    }
}
