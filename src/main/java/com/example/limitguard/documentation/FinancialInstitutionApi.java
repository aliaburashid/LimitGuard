package com.example.limitguard.documentation;

import com.example.limitguard.dto.FinancialInstitutionRequest;
import com.example.limitguard.dto.FinancialInstitutionDirectoryResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import com.example.limitguard.dto.FinancialInstitutionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface FinancialInstitutionApi {

    // this directory is public because users need it before registering
    @SecurityRequirements
    @Operation(
            summary = "Get the registration institution directory",
            description = """
                    Returns active financial institutions ordered by name, then ID.
                    Each item contains only the institution ID and name.
                    Returns an empty array when there are no active institutions.
                    No JWT is required. This endpoint does not change any records.
                    Institution creation and updates still require an ADMIN.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Active institution IDs and names")
    ResponseEntity<List<FinancialInstitutionDirectoryResponse>> getInstitutionDirectory();

    @Operation(
            summary = "Create a financial institution",
            description = """
                    Creates a new financial institution with a name and status.

                    **Business Rules:**
                    - The institution name must not already exist.
                    - A duplicate institution name returns `409 Conflict`.
                    - The institution's status is supplied in the request.
                    - The new institution is recorded in the audit log.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `ADMIN`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Financial institution created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data or missing required fields"),
            @ApiResponse(responseCode = "401", description = "Authentication is required or the JWT is invalid"),
            @ApiResponse(responseCode = "403", description = "Access denied. ADMIN role required"),
            @ApiResponse(responseCode = "409", description = "Financial institution already exists")
    })
    ResponseEntity<FinancialInstitutionResponse> createFinancialInstitution(
            FinancialInstitutionRequest financialInstitutionRequest
    );

    @Operation(
            summary = "Update a financial institution",
            description = """
                    Updates the name and status of an existing financial institution.

                    **Business Rules:**
                    - The financial institution must exist.
                    - The institution name must not conflict with another existing institution.
                    - Keeping the current name is allowed.
                    - A duplicate name returns `409 Conflict`.
                    - Successful updates are recorded in the audit log.
                    - Returns `404 Not Found` if the institution does not exist.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `ADMIN`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Financial institution updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data or missing required fields"),
            @ApiResponse(responseCode = "401", description = "Authentication is required or the JWT is invalid"),
            @ApiResponse(responseCode = "403", description = "Access denied. ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Financial institution not found"),
            @ApiResponse(responseCode = "409", description = "Financial institution conflicts with an existing record")
    })
    ResponseEntity<FinancialInstitutionResponse> updateFinancialInstitution(
            @Parameter(description = "ID of the financial institution to update", example = "1")
            Long institutionId,
            FinancialInstitutionRequest financialInstitutionRequest
    );
}
