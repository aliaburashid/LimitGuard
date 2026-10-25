package com.example.limitguard.documentation;

import com.example.limitguard.dto.AuditLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

public interface AuditLogApi {

    @Operation(
            summary = "Get audit log history",
            description = """
                    Retrieves a paginated history of LimitGuard audit logs.

                    **Features:**
                    - Returns audit log records as a paginated response.
                    - Supports pagination and sorting through request parameters.
                    - Returns results sorted by creation date, newest first by default.
                    - Provides audit history for reviewing recorded system activities.

                    **Pagination and Sorting:**
                    - Default page number: `0` (first page).
                    - Default page size: `10` records.
                    - Default sorting: `createdAt` descending.
                    - Use pagination parameters to navigate through audit records.

                    **Business Rules:**
                    - Only authorized administrators can retrieve audit log history.
                    - Audit records are returned through a read-only endpoint.
                    - This endpoint does not create, update, or delete audit records.

                    **Security:**
                    - Requires JWT Bearer authentication.
                    - Required role: `ADMIN`.
                    - Requests without valid authentication return `401 Unauthorized`.
                    - Authenticated users without the ADMIN role receive `403 Forbidden`.

                    **Response:**
                    - Returns a paginated collection of `AuditLogResponse` objects.
                    - Includes pagination metadata provided by Spring Data.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Audit logs retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination or sorting parameters"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required or the JWT is invalid"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied. ADMIN role required"
            )
    })
    ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @Parameter(
                    description = """
                            **Pagination and Sorting Options:**

                            - `page`: Zero-based page index (default: `0`).
                            - `size`: Number of records per page (default: `10`).
                            - `sort`: Sorting field and direction (default: `createdAt,desc`).

                            **Example:**
                            `?page=0&size=10&sort=createdAt,desc`
                            """
            )
            Pageable pageable
    );
}
