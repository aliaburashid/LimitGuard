package com.example.limitguard.controller;

import com.example.limitguard.dto.AuditLogResponse;
import com.example.limitguard.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    //--------------------------------------------------------

    // gets the audit log history
    // only an Admin is allowed to access this endpoint
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            // returns 10 records per page and shows the newest records first
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        // gets the requested page of audit logs from the service
        Page<AuditLogResponse> auditLogs = auditLogService.getAuditLogs(pageable);
        // returns the audit logs with 200 OK
        return new ResponseEntity<>(auditLogs, HttpStatus.OK);
    }
}