package com.example.limitguard.documentation;

import com.example.limitguard.dto.CreditRequestRequest;
import com.example.limitguard.dto.CreditRequestResponse;
import com.example.limitguard.dto.CreditRequestReviewResponse;
import com.example.limitguard.dto.RejectCreditRequestRequest;
import com.example.limitguard.enums.CreditRequestStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

public interface CreditRequestApi {

    // creates a new credit capacity request
    @Operation(
            summary = "Create a credit request",
            description = "Submits a new credit capacity request. Requires JWT authentication and the RELATIONSHIP_MANAGER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Credit request created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data or business rule violation"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. RELATIONSHIP_MANAGER role required"),
            @ApiResponse(responseCode = "404", description = "Referenced resource not found")
    })
    ResponseEntity<CreditRequestResponse> createCreditRequest(
            CreditRequestRequest creditRequestRequest
    );

    // gets the logged-in Relationship Manager's requests
    @Operation(
            summary = "Get my credit requests",
            description = "Retrieves requests submitted by the authenticated Relationship Manager. "
                    + "Supports pagination and sorting. Defaults: page 0, size 10, createdAt DESC."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credit requests retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or sorting parameters"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. RELATIONSHIP_MANAGER role required")
    })
    ResponseEntity<Page<CreditRequestResponse>> getMyCreditRequests(
            @Parameter(description = "Page number starting at zero", example = "0")
            int page,

            @Parameter(description = "Number of results per page", example = "10")
            int size,

            @Parameter(description = "Sorting field", example = "createdAt")
            String sortBy,

            @Parameter(description = "Sort direction: ASC or DESC", example = "DESC")
            String direction
    );

    // gets a credit request by ID
    @Operation(
            summary = "Get credit request by ID",
            description = "Retrieves a credit request by ID. Requires the RELATIONSHIP_MANAGER or RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credit request retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid credit request ID"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Credit request not found")
    })
    ResponseEntity<CreditRequestResponse> getCreditRequestById(
            @Parameter(description = "Credit request ID", example = "1")
            Long creditRequestId
    );

    // marks a reserved credit request as used
    @Operation(
            summary = "Mark credit request as used",
            description = "Marks a reserved credit request as used. Requires the RELATIONSHIP_MANAGER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credit request marked as used"),
            @ApiResponse(responseCode = "400", description = "Invalid request state or transition"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Credit request not found")
    })
    ResponseEntity<CreditRequestResponse> markCreditRequestAsUsed(
            @Parameter(description = "Reserved credit request ID", example = "1")
            Long creditRequestId
    );

    // cancels a reserved credit request
    @Operation(
            summary = "Cancel a credit request",
            description = "Cancels a reserved credit request. Requires the RELATIONSHIP_MANAGER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credit request cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request state or cancellation"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Credit request not found")
    })
    ResponseEntity<CreditRequestResponse> cancelCreditRequest(
            @Parameter(description = "Credit request ID to cancel", example = "1")
            Long creditRequestId
    );

    // gets requests waiting for approval
    @Operation(
            summary = "Get pending credit requests",
            description = "Retrieves requests awaiting Risk Officer approval. Supports pagination and sorting. "
                    + "Requires the RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pending credit requests retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or sorting parameters"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied. RISK_OFFICER role required")
    })
    ResponseEntity<Page<CreditRequestResponse>> getPendingCreditRequests(
            Pageable pageable
    );

    // reviews a credit request
    @Operation(
            summary = "Review a credit request",
            description = "Retrieves information needed to review a credit request. Requires the RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Review details retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid credit request ID or state"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Credit request not found")
    })
    ResponseEntity<CreditRequestReviewResponse> reviewCreditRequest(
            @Parameter(description = "Credit request ID to review", example = "1")
            Long creditRequestId
    );

    // approves a pending credit request
    @Operation(
            summary = "Approve a credit request",
            description = "Approves a pending credit request, subject to business rules and available headroom. "
                    + "Requires the RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credit request approved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid approval or insufficient headroom"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Credit request not found")
    })
    ResponseEntity<CreditRequestResponse> approveCreditRequest(
            @Parameter(description = "Pending credit request ID", example = "1")
            Long creditRequestId
    );

    // rejects a pending credit request
    @Operation(
            summary = "Reject a credit request",
            description = "Rejects a pending credit request with a reason. Requires the RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credit request rejected successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid rejection reason or request state"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Credit request not found")
    })
    ResponseEntity<CreditRequestResponse> rejectCreditRequest(
            @Parameter(description = "Credit request ID to reject", example = "1")
            Long creditRequestId,
            RejectCreditRequestRequest request
    );

    // searches and filters credit requests
    @Operation(
            summary = "Search and filter credit requests",
            description = "Searches credit requests by optional status, counterparty ID, and requester ID. "
                    + "Supports pagination and sorting. Requires the RISK_OFFICER role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credit requests retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid search or pagination parameters"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    ResponseEntity<Page<CreditRequestResponse>> searchCreditRequests(
            @Parameter(description = "Optional credit request status", example = "PENDING_APPROVAL")
            CreditRequestStatus status,

            @Parameter(description = "Optional counterparty ID", example = "1")
            Long counterpartyId,

            @Parameter(description = "Optional requester ID", example = "1")
            Long requesterId,

            Pageable pageable
    );
}
