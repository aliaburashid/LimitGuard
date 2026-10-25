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
            description = """
                    Creates a new credit limit for a counterparty within a financial institution.

                    **Business Rules:**
                    - The financial institution must exist.
                    - The counterparty must exist.
                    - A duplicate credit limit for the same institution and counterparty is not allowed.
                    - Credit limits are managed within the authenticated user's financial institution.
                    - Returns `409 Conflict` when a credit limit already exists for the same institution and counterparty.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `RISK_OFFICER`.
                    - Access to another financial institution's credit limits is denied.
                    """
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
            description = """
                    Retrieves the current exposure and available headroom for a credit limit.

                    **Exposure Details:**
                    - **Credit limit:** The maximum permitted credit capacity.
                    - **Used exposure:** Credit capacity already consumed by completed usage.
                    - **Reserved exposure:** Credit capacity allocated to approved or automatically reserved requests that have not yet been marked as used.
                    - **Available headroom:** The remaining credit capacity available for new requests.

                    **Calculation:**
                    - `Available Headroom = Credit Limit - Used Exposure - Reserved Exposure`

                    **Business Rules:**
                    - The credit limit must exist.
                    - Users can only access credit limits belonging to their financial institution.
                    - Returns `404 Not Found` when the credit limit does not exist.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Allowed roles: `RELATIONSHIP_MANAGER`, `RISK_OFFICER`, `ADMIN`.
                    """
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
            description = """
                    Updates the maximum permitted credit capacity of an existing credit limit.

                    **Business Rules:**
                    - The credit limit must exist.
                    - The new limit must not be lower than the combined used and reserved exposure.
                    - Increasing the limit increases the available headroom.
                    - Reducing the limit is permitted only when the existing exposure remains fully covered.
                    - An invalid reduction returns `400 Bad Request`.
                    - Users cannot update credit limits belonging to another financial institution.

                    **Example:**
                    - Current credit limit: `1,000,000`
                    - Used exposure: `400,000`
                    - Reserved exposure: `200,000`
                    - Minimum permitted new limit: `600,000`
                    - Updating the limit to `500,000` is rejected because it is below current exposure.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `RISK_OFFICER`.
                    """
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
