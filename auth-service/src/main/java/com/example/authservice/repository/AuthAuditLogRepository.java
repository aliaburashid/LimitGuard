package com.example.authservice.repository;

import com.example.authservice.model.AuthAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, Long> {
}
