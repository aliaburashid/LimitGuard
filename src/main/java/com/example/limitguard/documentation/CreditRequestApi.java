
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
            description = """
                    Submits a new credit capacity request.

                    **Business Rules:**
                    - Requests of 500,000 or less reserve credit capacity immediately.
                    - Requests above 500,000 require Risk Officer approval.
                    - Reserved capacity expires after 24 hours.
                    - Requests cannot exceed available headroom.
                    - Requests cannot be created for frozen or closed counterparties.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `RELATIONSHIP_MANAGER`.
                    """
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
            description = """
                    Retrieves credit requests submitted by the authenticated Relationship Manager.

                    **Features:**
                    - Returns only requests submitted by the current user.
                    - Supports pagination and sorting.
                    - Default page: `0`.
                    - Default page size: `10`.
                    - Default sorting: `createdAt DESC`.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `RELATIONSHIP_MANAGER`.
                    """
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
            description = """
                    Retrieves a credit request using its unique ID.

                    **Business Rules:**
                    - Relationship Managers can retrieve their own requests.
                    - Risk Officers can retrieve requests within their financial institution.
                    - Access to requests belonging to another institution is denied.
                    - Returns `404` when the requested credit request does not exist.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Allowed roles: `RELATIONSHIP_MANAGER`, `RISK_OFFICER`.
                    """
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
            description = """
                    Marks a reserved credit request as used.

                    **Business Rules:**
                    - The request must currently have the `RESERVED` status.
                    - Only the Relationship Manager who created the request can mark it as used.
                    - Changes the request status from `RESERVED` to `USED`.
                    - Requests in other states cannot be marked as used.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `RELATIONSHIP_MANAGER`.
                    """
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
            description = """
                    Cancels a reserved credit request and releases its reserved credit capacity.

                    **Business Rules:**
                    - The request must currently have the `RESERVED` status.
                    - Only the Relationship Manager who created the request can cancel it.
                    - Cancelling releases the reserved credit capacity.
                    - Requests in other states cannot be cancelled through this endpoint.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `RELATIONSHIP_MANAGER`.
                    """
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
            description = """
                    Retrieves credit requests awaiting Risk Officer approval.

                    **Features:**
                    - Returns requests with the `PENDING_APPROVAL` status.
                    - Requests above 500,000 require Risk Officer approval before capacity is reserved.
                    - Supports pagination and sorting.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `RISK_OFFICER`.
                    """
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
            description = """
                    Retrieves the information required to review a pending credit request.

                    **Business Rules:**
                    - The request must have the `PENDING_APPROVAL` status.
                    - Only Risk Officers can review pending requests.
                    - Risk Officers can review requests within their financial institution.
                    - Requests from another institution cannot be reviewed.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `RISK_OFFICER`.
                    """
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
            description = """
                    Approves a pending credit request and reserves the required credit capacity.

                    **Business Rules:**
                    - The request must have the `PENDING_APPROVAL` status.
                    - Only Risk Officers can approve pending requests.
                    - A Risk Officer cannot approve a request they originally submitted (maker-checker rule).
                    - Approval requires sufficient available credit headroom.
                    - Approved requests move to the `RESERVED` status.
                    - Reserved requests expire after 24 hours if not marked as used.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `RISK_OFFICER`.
                    """
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
            description = """
                    Rejects a pending credit request and records the rejection reason.

                    **Business Rules:**
                    - The request must have the `PENDING_APPROVAL` status.
                    - Only Risk Officers can reject pending requests.
                    - A Risk Officer cannot reject a request they originally submitted (maker-checker rule).
                    - A rejection reason must be provided.
                    - Rejected requests cannot be approved later through the normal approval endpoint.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `RISK_OFFICER`.
                    """
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
            description = """
                    Searches and filters credit requests for review and monitoring.

                    **Available Filters:**
                    - Request status, such as `PENDING_APPROVAL`, `RESERVED`, or `USED`.
                    - Counterparty ID.
                    - Requester ID.

                    **Features:**
                    - All filters are optional.
                    - Supports pagination and sorting.

                    **Security:**
                    - JWT Bearer authentication required.
                    - Required role: `RISK_OFFICER`.
                    """
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
