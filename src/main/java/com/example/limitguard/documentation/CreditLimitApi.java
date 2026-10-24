package com.example.limitguard.documentation;

import com.example.limitguard.dto.CreditExposureResponse;
import com.example.limitguard.dto.CreditLimitRequest;
import com.example.limitguard.dto.CreditLimitResponse;
import com.example.limitguard.dto.UpdateCreditLimitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

public interface CreditLimitApi {

    @Operation(
            summary = "Create a credit limit",
            description = "Creates a new credit limit. Requires JWT authentication and the RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Credit limit created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid credit limit request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. RISK_OFFICER role required"),
            @ApiResponse(responseCode = "404", description = "Financial institution or counterparty not found"),
            @ApiResponse(responseCode = "409", description = "Credit limit already exists")
    })
    ResponseEntity<CreditLimitResponse> createCreditLimit(
            CreditLimitRequest creditLimitRequest
    );

    @Operation(
            summary = "Get credit limit exposure and available headroom",
            description = "Retrieves the current credit exposure and available headroom. "
                    + "Requires the RELATIONSHIP_MANAGER, RISK_OFFICER, or ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credit exposure retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid credit limit ID"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. Required role missing"),
            @ApiResponse(responseCode = "404", description = "Credit limit not found")
    })
    ResponseEntity<CreditExposureResponse> getCreditExposure(
            @Parameter(description = "ID of the credit limit", example = "1")
            Long creditLimitId
    );

    @Operation(
            summary = "Update a credit limit",
            description = "Updates an existing credit limit. The new limit cannot be lower than current exposure. "
                    + "Requires JWT authentication and the RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credit limit updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data or credit limit reduction below current exposure"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. RISK_OFFICER role required"),
            @ApiResponse(responseCode = "404", description = "Credit limit not found")
    })
    ResponseEntity<CreditLimitResponse> updateCreditLimit(
            @Parameter(description = "ID of the credit limit to update", example = "1")
            Long creditLimitId,
            UpdateCreditLimitRequest updateCreditLimitRequest
    );
}
