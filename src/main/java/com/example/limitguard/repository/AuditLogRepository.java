package com.example.limitguard.repository;

import com.example.limitguard.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // gets audit logs created by users from one bank
    Page<AuditLog> findByActorFinancialInstitutionId(Long institutionId, Pageable pageable);

    // gets all audit logs belonging to one bank
    Page<AuditLog> findByFinancialInstitutionId(Long institutionId, Pageable pageable);

}
