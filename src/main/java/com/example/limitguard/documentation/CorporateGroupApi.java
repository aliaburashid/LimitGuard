package com.example.limitguard.documentation;

import com.example.limitguard.dto.CorporateGroupRequest;
import com.example.limitguard.dto.CorporateGroupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface CorporateGroupApi {

    @Operation(
            summary = "Create a corporate group",
            description = """
                    Creates a new corporate group in the system.

                    **Business Rules:**
                    - A corporate group must have a name.
                    - The group name cannot be null, empty, or blank.
                    - A corporate group with the same name cannot be created twice.
                    - Returns `409 Conflict` when a duplicate corporate group already exists.
                    - Newly created corporate groups can later be linked to counterparties through group memberships.
                    - Creating a corporate group does not automatically create a group credit limit.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Allowed roles: `ADMIN`, `RISK_OFFICER`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Corporate group created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid corporate group request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. ADMIN or RISK_OFFICER role required"),
            @ApiResponse(responseCode = "409", description = "Corporate group already exists")
    })
    ResponseEntity<CorporateGroupResponse> createGroup(
            CorporateGroupRequest request
    );

    @Operation(
            summary = "Get corporate group by ID",
            description = """
                    Retrieves an existing corporate group using its unique ID.

                    **Business Rules:**
                    - The corporate group must already exist.
                    - Returns `404 Not Found` when the group does not exist.
                    - Returns the corporate group's stored information, including its creation and update timestamps.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Allowed roles: `RELATIONSHIP_MANAGER`, `RISK_OFFICER`, `ADMIN`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Corporate group retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid corporate group ID"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. Required role missing"),
            @ApiResponse(responseCode = "404", description = "Corporate group not found")
    })
    ResponseEntity<CorporateGroupResponse> getGroupById(
            @Parameter(description = "ID of the corporate group", example = "1")
            Long id
    );

    @Operation(
            summary = "Get all corporate groups",
            description = """
                    Retrieves all registered corporate groups.

                    **Features:**
                    - Returns a list of corporate groups.
                    - Returns an empty list when no corporate groups exist.
                    - Each corporate group includes its ID, name, and timestamps.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Allowed roles: `RELATIONSHIP_MANAGER`, `RISK_OFFICER`, `ADMIN`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Corporate groups retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. Required role missing")
    })
    ResponseEntity<List<CorporateGroupResponse>> getAllGroups();

    @Operation(
            summary = "Update a corporate group",
            description = """
                    Updates the name of an existing corporate group.

                    **Business Rules:**
                    - The corporate group must already exist.
                    - The updated name cannot be null, empty, or blank.
                    - The updated name must not conflict with another existing corporate group.
                    - Returns `409 Conflict` when another group already has the requested name.
                    - Updating the group name does not change its unique ID.
                    - The update timestamp is maintained automatically.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Allowed roles: `ADMIN`, `RISK_OFFICER`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Corporate group updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid corporate group request data or ID"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. ADMIN or RISK_OFFICER role required"),
            @ApiResponse(responseCode = "404", description = "Corporate group not found"),
            @ApiResponse(responseCode = "409", description = "Corporate group name already exists")
    })
    ResponseEntity<CorporateGroupResponse> updateGroup(
            @Parameter(description = "ID of the corporate group to update", example = "1")
            Long id,
            CorporateGroupRequest request
    );

    @Operation(
            summary = "Delete a corporate group",
            description = """
                    Deletes an existing corporate group.

                    **Business Rules:**
                    - The corporate group must already exist.
                    - Returns `404 Not Found` when the group does not exist.
                    - A successful deletion returns `204 No Content`.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Allowed roles: `ADMIN`, `RISK_OFFICER`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Corporate group deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid corporate group ID"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. ADMIN or RISK_OFFICER role required"),
            @ApiResponse(responseCode = "404", description = "Corporate group not found")
    })
    ResponseEntity<Void> deleteGroup(
            @Parameter(description = "ID of the corporate group to delete", example = "1")
            Long id
    );
}
