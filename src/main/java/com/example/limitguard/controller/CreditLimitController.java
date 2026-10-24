package com.example.limitguard.controller;

import com.example.limitguard.dto.CreditExposureResponse;
import com.example.limitguard.dto.CreditLimitRequest;
import com.example.limitguard.dto.CreditLimitResponse;
import com.example.limitguard.dto.UpdateCreditLimitRequest;
import com.example.limitguard.service.CreditLimitService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/credit-limits")
public class CreditLimitController {

    @Autowired
    private CreditLimitService creditLimitService;

    // creates a new credit limit
    @PostMapping
    @PreAuthorize("hasRole('RISK_OFFICER')")
    @Operation(
            summary = "Create a credit limit",
            description = "Creates a new credit limit. Requires JWT authentication and the RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Credit limit created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid credit limit request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. RISK_OFFICER role required"),
            @ApiResponse(responseCode = "404", description = "Referenced resource not found"),
            @ApiResponse(responseCode = "409", description = "Credit limit already exists")
    })
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
    @Operation(
            summary = "Get credit limit exposure and available headroom",
            description = "Retrieves the credit exposure and remaining available headroom for a credit limit. "
                    + "Requires JWT authentication and the RELATIONSHIP_MANAGER, RISK_OFFICER, or ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credit exposure retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid credit limit ID"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. Required role missing"),
            @ApiResponse(responseCode = "404", description = "Credit limit not found")
    })
    public ResponseEntity<CreditExposureResponse> getCreditExposure(
            @Parameter(description = "ID of the credit limit", example = "1")
            @PathVariable Long creditLimitId) {
        CreditExposureResponse creditExposureResponse = creditLimitService.getCreditExposure(creditLimitId);
        return new ResponseEntity<>(creditExposureResponse, HttpStatus.OK);
    }

    // updates an existing credit limit
    @PutMapping("/{creditLimitId}")
    @PreAuthorize("hasRole('RISK_OFFICER')")
    @Operation(
            summary = "Update a credit limit",
            description = "Updates an existing credit limit using its ID. Requires JWT authentication and the RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credit limit updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data or credit limit reduction below current exposure"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. RISK_OFFICER role required"),
            @ApiResponse(responseCode = "404", description = "Credit limit not found")
    })
    public ResponseEntity<CreditLimitResponse> updateCreditLimit(
            @Parameter(description = "ID of the credit limit to update", example = "1")
            @PathVariable Long creditLimitId,
            @Valid @RequestBody UpdateCreditLimitRequest updateCreditLimitRequest) {
        CreditLimitResponse creditLimitResponse =
                creditLimitService.updateCreditLimit(creditLimitId, updateCreditLimitRequest);
        return new ResponseEntity<>(creditLimitResponse, HttpStatus.OK);
    }
}
