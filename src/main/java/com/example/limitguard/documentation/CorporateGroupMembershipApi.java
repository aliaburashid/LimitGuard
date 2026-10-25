package com.example.limitguard.documentation;

import com.example.limitguard.dto.CorporateGroupMembershipRequest;
import com.example.limitguard.dto.CorporateGroupMembershipResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.http.ResponseEntity;

import java.util.List;

public interface CorporateGroupMembershipApi {

    // documents how to create a membership
    @Operation(
            summary = "Create a corporate group membership",
            description = """
                    Assigns a counterparty to a corporate group.

                    Business rules:
                    - The corporate group must exist.
                    - The counterparty must exist.
                    - A counterparty can belong to only one corporate group.

                    Security:
                    - JWT authentication required.
                    - Allowed roles: ADMIN, RISK_OFFICER.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Membership created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid membership information"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Corporate group or counterparty not found"),
            @ApiResponse(responseCode = "409", description = "Counterparty already belongs to a group")
    })
    ResponseEntity<CorporateGroupMembershipResponse> createMembership(
            CorporateGroupMembershipRequest request);


    // documents how to get all memberships
    @Operation(
            summary = "Get all corporate group memberships",
            description = """
                    Returns all memberships.

                    Security:
                    - JWT authentication required.
                    - Allowed roles: ADMIN, RISK_OFFICER, RELATIONSHIP_MANAGER.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Memberships retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    ResponseEntity<List<CorporateGroupMembershipResponse>> getAllMemberships();


    // documents how to get one membership
    @Operation(
            summary = "Get membership by ID",
            description = """
                    Returns one membership using its ID.

                    Security:
                    - JWT authentication required.
                    - Allowed roles: ADMIN, RISK_OFFICER, RELATIONSHIP_MANAGER.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Membership retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Membership not found")
    })
    ResponseEntity<CorporateGroupMembershipResponse> getMembershipById(
            Long membershipId);


    // documents how to get memberships for one group
    @Operation(
            summary = "Get memberships by corporate group ID",
            description = """
                    Returns all counterparties assigned to a corporate group.

                    Security:
                    - JWT authentication required.
                    - Allowed roles: ADMIN, RISK_OFFICER, RELATIONSHIP_MANAGER.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group memberships retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Corporate group not found")
    })
    ResponseEntity<List<CorporateGroupMembershipResponse>> getMembershipsByGroupId(
            Long groupId);


    // documents how to delete a membership
    @Operation(
            summary = "Delete a corporate group membership",
            description = """
                    Removes a counterparty from its corporate group.

                    The counterparty itself is not deleted.

                    Security:
                    - JWT authentication required.
                    - Allowed roles: ADMIN, RISK_OFFICER.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Membership deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Membership not found")
    })
    ResponseEntity<Void> deleteMembership(Long membershipId);
}