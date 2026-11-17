package com.example.creditservice.service;

import com.example.creditservice.client.AuthServiceClient;
import com.example.creditservice.dto.AuditLogResponse;
import com.example.creditservice.dto.InternalUserDisplayResponse;
import com.example.creditservice.model.AuditLog;
import com.example.creditservice.repository.AuditLogRepository;
import com.example.creditservice.security.CurrentUserSecurity;
import com.example.creditservice.security.InstitutionAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final InstitutionAccessService institutionAccessService;
    private final AuthServiceClient authServiceClient;

    // Scope the audit page by the event's institution, including events without an actor.
    public Page<AuditLogResponse> getAuditLogs(Pageable pageable) {
        Long institutionId = institutionAccessService.getCurrentInstitutionId();
        return auditLogRepository.findByFinancialInstitutionId(institutionId, pageable)
                .map(this::convertToResponse);
    }

    private AuditLogResponse convertToResponse(AuditLog auditLog) {
        Long actorId = auditLog.getActorId();
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getDetails(),
                actorId,
                actorName(actorId),
                auditLog.getCreatedAt()
        );
    }

    private String actorName(Long actorId) {
        if (actorId == null) {
            return "SYSTEM";
        }

        try {
            InternalUserDisplayResponse actor =
                    authServiceClient.getUserDisplay(actorId, CurrentUserSecurity.getCurrentJwt());
            if (actor != null && actor.getFullName() != null && !actor.getFullName().isBlank()) {
                return actor.getFullName();
            }
        } catch (RestClientException exception) {
            // Keep the event visible if Auth cannot provide the actor's display name.
        }
        return "Unavailable";
    }
}
