package com.example.limitguard.service;

import com.example.limitguard.dto.AuditLogResponse;
import com.example.limitguard.model.AuditLog;
import com.example.limitguard.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    //-----------------------------------------------------------------

    // gets the audit history using pagination and sorting
    public Page<AuditLogResponse> getAuditLogs(Pageable pageable) {
        // gets a page of AuditLog records from the database
        Page<AuditLog> auditLogs = auditLogRepository.findAll(pageable);
        // converts each AuditLog entity into an AuditLogResponse
        // while keeping the same pagination information
        return auditLogs.map(this::convertToResponse);
    }

    // converts the AuditLog database object into an AuditLogResponse
    // so we only return the information we want to show in the API
    private AuditLogResponse convertToResponse(AuditLog auditLog) {
        // starts with no actor because some actions are performed automatically by the system
        Long actorId = null;
        String actorName = "SYSTEM";

        // if the action was performed by a logged-in user,
        // get their id and full name
        if (auditLog.getActor() != null) {
            actorId = auditLog.getActor().getId();
            actorName = auditLog.getActor().getFirstName() + " " + auditLog.getActor().getLastName();
        }

        // creates and returns the response using the audit log information
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getDetails(),
                actorId,
                actorName,
                auditLog.getCreatedAt()
        );
    }
}