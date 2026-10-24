package com.example.limitguard.documentation;

import com.example.limitguard.dto.CounterpartyRequest;
import com.example.limitguard.dto.CounterpartyResponse;
import com.example.limitguard.dto.CounterpartyStatusRequest;
import com.example.limitguard.enums.CounterpartyStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

public interface CounterpartyApi {

    @Operation(
            summary = "Create a counterparty",
            description = "Creates a new counterparty. Requires JWT authentication and the RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Counterparty created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid counterparty request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. RISK_OFFICER role required"),
            @ApiResponse(responseCode = "409", description = "Counterparty already exists")
    })
    ResponseEntity<CounterpartyResponse> createCounterparty(
            CounterpartyRequest counterpartyRequest
    );

    @Operation(
            summary = "Get counterparty by ID",
            description = "Retrieves a counterparty using its ID. Requires JWT authentication and the RELATIONSHIP_MANAGER, RISK_OFFICER, or ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Counterparty retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid counterparty ID"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. Required role missing"),
            @ApiResponse(responseCode = "404", description = "Counterparty not found")
    })
    ResponseEntity<CounterpartyResponse> getCounterpartyById(
            @Parameter(description = "ID of the counterparty", example = "1")
            Long counterpartyId
    );

    @Operation(
            summary = "Get, search, and filter counterparties",
            description = "Retrieves counterparties with optional name search, status filtering, pagination, and sorting. "
                    + "Default page is 0, size is 10, and sorting is by name ascending. "
                    + "Requires JWT authentication and the RELATIONSHIP_MANAGER, RISK_OFFICER, or ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Paginated counterparties retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or pagination parameters"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. Required role missing")
    })
    ResponseEntity<Page<CounterpartyResponse>> getCounterparties(
            @Parameter(description = "Optional counterparty name search", example = "Bahrain")
            String name,

            @Parameter(description = "Optional counterparty status filter", example = "ACTIVE")
            CounterpartyStatus status,

            Pageable pageable
    );

    @Operation(
            summary = "Update a counterparty",
            description = "Updates an existing counterparty using its ID. Requires JWT authentication and the RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Counterparty updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid counterparty request data or ID"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. RISK_OFFICER role required"),
            @ApiResponse(responseCode = "404", description = "Counterparty not found"),
            @ApiResponse(responseCode = "409", description = "Counterparty name already exists")
    })
    ResponseEntity<CounterpartyResponse> updateCounterparty(
            @Parameter(description = "ID of the counterparty to update", example = "1")
            Long counterpartyId,
            CounterpartyRequest counterpartyRequest
    );

    @Operation(
            summary = "Update counterparty status",
            description = "Updates the status of an existing counterparty. Requires JWT authentication and the RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Counterparty status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid counterparty status or ID"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. RISK_OFFICER role required"),
            @ApiResponse(responseCode = "404", description = "Counterparty not found")
    })
    ResponseEntity<CounterpartyResponse> updateCounterpartyStatus(
            @Parameter(description = "ID of the counterparty to update", example = "1")
            Long counterpartyId,
            CounterpartyStatusRequest statusRequest
    );
}
