
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
            description = """
                    Creates a new counterparty in the system.

                    **Business Rules:**
                    - A counterparty with the same name cannot be created twice.
                    - Newly created counterparties receive the `ACTIVE` status.
                    - Returns `409 Conflict` when a duplicate counterparty already exists.
                    - Active counterparties can be used when creating credit requests, subject to credit limit and headroom checks.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Allowed roles: `ADMIN`, `RISK_OFFICER`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Counterparty created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid counterparty request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. ADMIN or RISK_OFFICER role required"),
            @ApiResponse(responseCode = "409", description = "Counterparty already exists")
    })
    ResponseEntity<CounterpartyResponse> createCounterparty(
            CounterpartyRequest counterpartyRequest
    );

    @Operation(
            summary = "Get counterparty by ID",
            description = """
                    Retrieves an existing counterparty using its unique ID.

                    **Features:**
                    - Returns the counterparty's stored information, including its current status.
                    - Returns `404 Not Found` when the counterparty does not exist.

                    **Counterparty Statuses:**
                    - `ACTIVE`: Eligible for new credit requests, subject to other business rules.
                    - `FROZEN`: New credit requests are blocked.
                    - `CLOSED`: New credit requests are blocked.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Allowed roles: `RELATIONSHIP_MANAGER`, `RISK_OFFICER`, `ADMIN`.
                    """
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
            description = """
                    Retrieves a paginated list of counterparties with optional search and filtering.

                    **Available Filters:**
                    - Search counterparties by name.
                    - Filter by status: `ACTIVE`, `FROZEN`, or `CLOSED`.
                    - Filters can be used individually or together.

                    **Pagination and Sorting:**
                    - Default page: `0`.
                    - Default page size: `10`.
                    - Default sorting: `name ASC`.
                    - Supports custom pagination and sorting.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Allowed roles: `RELATIONSHIP_MANAGER`, `RISK_OFFICER`, `ADMIN`.
                    """
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
            description = """
                    Updates the stored information of an existing counterparty.

                    **Business Rules:**
                    - The counterparty must already exist.
                    - The updated name must not conflict with another existing counterparty.
                    - A duplicate name returns `409 Conflict`.
                    - Updating counterparty information does not change its current status.
                    - To change the status, use the dedicated Update Counterparty Status endpoint.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Allowed roles: `ADMIN`, `RISK_OFFICER`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Counterparty updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid counterparty request data or ID"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. ADMIN or RISK_OFFICER role required"),
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
            description = """
                    Changes the status of an existing counterparty.

                    **Available Statuses:**
                    - `ACTIVE`: Allows new credit requests, subject to available credit capacity.
                    - `FROZEN`: Blocks the creation of new credit requests.
                    - `CLOSED`: Blocks the creation of new credit requests.

                    **Business Rules:**
                    - The counterparty must already exist.
                    - Status changes affect whether new credit requests can be created.
                    - Updating the status is separate from updating counterparty information.
                    - Returns `404 Not Found` if the counterparty does not exist.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Allowed roles: `ADMIN`, `RISK_OFFICER`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Counterparty status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid counterparty status or ID"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. ADMIN or RISK_OFFICER role required"),
            @ApiResponse(responseCode = "404", description = "Counterparty not found")
    })
    ResponseEntity<CounterpartyResponse> updateCounterpartyStatus(
            @Parameter(description = "ID of the counterparty to update", example = "1")
            Long counterpartyId,
            CounterpartyStatusRequest statusRequest
    );
}
