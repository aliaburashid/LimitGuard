package com.example.limitguard.documentation;

import com.example.limitguard.dto.ErrorResponse;
import com.example.limitguard.dto.GroupCreditLimitRequest;
import com.example.limitguard.dto.GroupCreditLimitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

public interface GroupCreditLimitApi {

    // documents the endpoint for creating a corporate group credit limit
    @Operation(
            summary = "Create group credit limit",
            description = """
                    Assigns a positive credit limit amount to an existing corporate group.

                    Business Rules:
                    - The corporate group must exist.
                    - The credit limit amount must be greater than zero.
                    - Each financial institution can assign its own credit limit to the same corporate group.
                    - A financial institution can have only one credit limit per corporate group.
                    - The financial institution is automatically identified from the logged-in user.
                    - Creating a duplicate group credit limit returns `409 Conflict`.
                    - A corporate group that does not exist returns `404 Not Found`.
                    - Successful creation is recorded in the audit log.

                    Security:
                    - JWT Bearer authentication required.
                    - Allowed roles: `ADMIN`, `RISK_OFFICER`.
                    - Users can create group credit limits only for their own financial institution.
                    """,
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Group credit limit created successfully",
                    content = @Content(
                            schema = @Schema(
                                    implementation = GroupCreditLimitResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid corporate group ID or credit limit amount",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User does not have permission to create group credit limits"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Corporate group not found",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Financial institution already has a credit limit for this corporate group",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<GroupCreditLimitResponse> createGroupCreditLimit(
            @Valid @RequestBody GroupCreditLimitRequest request
    );
}
