package com.example.limitguard.documentation;

import com.example.limitguard.dto.ErrorResponse;
import com.example.limitguard.dto.GroupCreditLimitRequest;
import com.example.limitguard.dto.GroupCreditLimitResponse;
import com.example.limitguard.dto.UpdateGroupCreditLimitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

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


    // documents the endpoint for retrieving one group credit limit
    @Operation(
            summary = "Get group credit limit by ID",
            description = """
                    Retrieves an existing group credit limit by its ID.

                    Business Rules:
                    - The group credit limit must exist.
                    - The group credit limit must belong to the logged-in user's financial institution.
                    - The response includes the corporate group, financial institution, approved amount, and timestamps.
                    - A missing or inaccessible group credit limit returns `404 Not Found`.
                    - Retrieving a group credit limit does not modify the database.

                    Security:
                    - JWT Bearer authentication required.
                    - Allowed roles: `ADMIN`, `RISK_OFFICER`, `RELATIONSHIP_MANAGER`.
                    - Users can retrieve only their own financial institution's group credit limits.
                    """,
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Group credit limit retrieved successfully",
                    content = @Content(
                            schema = @Schema(implementation = GroupCreditLimitResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User does not have permission to view group credit limits"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Group credit limit not found for this financial institution",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<GroupCreditLimitResponse> getGroupCreditLimitById(
            @PathVariable Long id
    );


    // documents the endpoint for retrieving all group credit limits
    @Operation(
            summary = "Get all group credit limits",
            description = """
                    Retrieves a paginated list of group credit limits for the logged-in user's bank.

                    Business Rules:
                    - Only group credit limits belonging to the logged-in user's financial institution are returned.
                    - Pagination and sorting are supported.
                    - Default page size is 10.
                    - Results are sorted by creation date in descending order by default.
                    - An empty result returns `200 OK` with an empty page.
                    - Retrieving group credit limits does not modify the database.

                    Security:
                    - JWT Bearer authentication required.
                    - Allowed roles: `ADMIN`, `RISK_OFFICER`, `RELATIONSHIP_MANAGER`.
                    - Users cannot view another financial institution's group credit limits.
                    """,
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Group credit limits retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination or sorting parameters",
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
                    description = "User does not have permission to view group credit limits"
            )
    })
    ResponseEntity<Page<GroupCreditLimitResponse>> getAllGroupCreditLimits(
            Pageable pageable
    );


    // documents the endpoint for updating a group credit limit
    @Operation(
            summary = "Update group credit limit",
            description = """
                    Updates the approved credit limit amount for an existing corporate group.

                    Business Rules:
                    - The group credit limit must exist.
                    - The group credit limit must belong to the logged-in user's financial institution.
                    - The updated credit limit amount must be greater than zero.
                    - The updated credit limit cannot be lower than the group's current combined exposure.
                    - Only the approved credit limit amount can be changed.
                    - The corporate group and financial institution cannot be changed.
                    - A missing or inaccessible group credit limit returns `404 Not Found`.
                    - A successful update returns `200 OK`.
                    - The old and new amounts are recorded in the audit log.

                    Security:
                    - JWT Bearer authentication required.
                    - Allowed roles: `ADMIN`, `RISK_OFFICER`.
                    - `RELATIONSHIP_MANAGER` cannot update group credit limits.
                    - Users cannot update another financial institution's group credit limits.
                    """,
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Group credit limit updated successfully",
                    content = @Content(
                            schema = @Schema(implementation = GroupCreditLimitResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid credit limit amount or amount below current group exposure",
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
                    description = "User does not have permission to update group credit limits"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Group credit limit not found for this financial institution",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<GroupCreditLimitResponse> updateGroupCreditLimit(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGroupCreditLimitRequest request
    );


    // documents the endpoint for calculating corporate group exposure
    @Operation(
            summary = "Get corporate group exposure",
            description = """
                    Calculates the combined used and reserved credit exposure
                    of all counterparties belonging to a corporate group.

                    Business Rules:
                    - The corporate group must exist.
                    - Exposure is calculated as the sum of used and reserved amounts.
                    - Only credit limits belonging to the logged-in user's financial institution are included.
                    - A corporate group with no credit exposure returns zero.
                    - Calculating exposure does not modify the database.

                    Security:
                    - JWT Bearer authentication required.
                    - Allowed roles: `ADMIN`, `RISK_OFFICER`, `RELATIONSHIP_MANAGER`.
                    - Users can view exposure only for their own financial institution.
                    """,
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Corporate group exposure calculated successfully",
                    content = @Content(
                            schema = @Schema(implementation = BigDecimal.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User does not have permission to view group exposure"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Corporate group not found",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<BigDecimal> getGroupExposure(
            @PathVariable Long corporateGroupId
    );


    // documents the endpoint for calculating corporate group headroom
    @Operation(
            summary = "Get corporate group headroom",
            description = """
                    Calculates the remaining available credit headroom
                    for a corporate group.

                    Business Rules:
                    - The corporate group must have an approved group credit limit for the logged-in user's financial institution.
                    - Group headroom is calculated as approved group limit minus combined group exposure.
                    - Combined exposure includes used and reserved amounts of all group counterparties.
                    - Only credit limits belonging to the logged-in user's financial institution are included.
                    - A negative headroom indicates that the group has exceeded its approved limit.
                    - Calculating headroom does not modify the database.

                    Security:
                    - JWT Bearer authentication required.
                    - Allowed roles: `ADMIN`, `RISK_OFFICER`, `RELATIONSHIP_MANAGER`.
                    - Users can view headroom only for their own financial institution.
                    """,
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Corporate group headroom calculated successfully",
                    content = @Content(
                            schema = @Schema(implementation = BigDecimal.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User does not have permission to view group headroom"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Group credit limit not found for this financial institution",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<BigDecimal> getGroupHeadroom(
            @PathVariable Long corporateGroupId
    );

}
