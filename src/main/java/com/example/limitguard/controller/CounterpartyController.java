package com.example.limitguard.controller;

import com.example.limitguard.dto.CounterpartyRequest;
import com.example.limitguard.dto.CounterpartyResponse;
import com.example.limitguard.dto.CounterpartyStatusRequest;
import com.example.limitguard.enums.CounterpartyStatus;
import com.example.limitguard.service.CounterpartyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/counterparties")
public class CounterpartyController {

    @Autowired
    private CounterpartyService counterpartyService;

    // creates a new counterparty
    @PostMapping
    @PreAuthorize("hasRole('RISK_OFFICER')")
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
    public ResponseEntity<CounterpartyResponse> createCounterparty(
            // gets and validates the counterparty information from the request body
            @Valid @RequestBody CounterpartyRequest counterpartyRequest) {

        // sends the request information to the service to create the counterparty
        CounterpartyResponse counterpartyResponse = counterpartyService.createCounterparty(counterpartyRequest);
        // returns the created counterparty with 201 Created
        return new ResponseEntity<>(counterpartyResponse, HttpStatus.CREATED);
    }

    // gets one counterparty by its id
    @GetMapping("/{counterpartyId}")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER', 'RISK_OFFICER', 'ADMIN')")
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
    public ResponseEntity<CounterpartyResponse> getCounterpartyById(
            @Parameter(description = "ID of the counterparty", example = "1")
            @PathVariable Long counterpartyId) {

        CounterpartyResponse counterpartyResponse = counterpartyService.getCounterpartyById(counterpartyId);
        return new ResponseEntity<>(counterpartyResponse, HttpStatus.OK);
    }

    // gets all counterparties or searches/filters counterparties by name and status
    // also supports pagination and sorting
    @GetMapping
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER', 'RISK_OFFICER', 'ADMIN')")
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
    public ResponseEntity<Page<CounterpartyResponse>> getCounterparties(

            // name is optional
            // example: /api/counterparties?name=Bahrain
            @Parameter(description = "Optional counterparty name search", example = "Bahrain")
            @RequestParam(required = false) String name,

            // status is optional
            // example: /api/counterparties?status=ACTIVE
            @Parameter(description = "Optional counterparty status filter", example = "ACTIVE")
            @RequestParam(required = false) CounterpartyStatus status,

            // default page = 0
            // default size = 10
            // default sorting = name ascending
            @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "name",
                    direction = Sort.Direction.ASC
            ) Pageable pageable) {

        Page<CounterpartyResponse> counterparties =
                counterpartyService.getCounterparties(
                        name,
                        status,
                        pageable
                );

        return new ResponseEntity<>(counterparties, HttpStatus.OK);
    }

    @PutMapping("/{counterpartyId}")
    @PreAuthorize("hasRole('RISK_OFFICER')")
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
    public ResponseEntity<CounterpartyResponse> updateCounterparty(
            @Parameter(description = "ID of the counterparty to update", example = "1")
            @PathVariable Long counterpartyId,
            @Valid @RequestBody CounterpartyRequest counterpartyRequest) {

        // sends the counterparty ID and new information to the service
        CounterpartyResponse counterpartyResponse =
                counterpartyService.updateCounterparty(counterpartyId, counterpartyRequest);

        // returns the updated counterparty with 200 OK
        return new ResponseEntity<>(counterpartyResponse, HttpStatus.OK);
    }

    @PatchMapping("/{counterpartyId}/status")
    @PreAuthorize("hasRole('RISK_OFFICER')")
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
    public ResponseEntity<CounterpartyResponse> updateCounterpartyStatus(
            @Parameter(description = "ID of the counterparty to update", example = "1")
            @PathVariable Long counterpartyId,
            @Valid @RequestBody CounterpartyStatusRequest statusRequest) {

        // sends the counterparty id and requested status to the service
        CounterpartyResponse counterpartyResponse =
                counterpartyService.updateCounterpartyStatus(counterpartyId, statusRequest);

        // returns the counterparty with its updated status
        return new ResponseEntity<>(counterpartyResponse, HttpStatus.OK);
    }

}
