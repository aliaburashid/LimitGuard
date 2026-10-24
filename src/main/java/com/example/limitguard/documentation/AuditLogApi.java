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
            description = "Retrieves a paginated history of audit logs. "
                    + "Results are sorted by creation date, newest first by default. "
                    + "Requires JWT authentication and the ADMIN role."
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
                    description = "Pagination and sorting options. "
                            + "Default page size is 10, sorted by createdAt descending."
            )
            Pageable pageable
    );
}
