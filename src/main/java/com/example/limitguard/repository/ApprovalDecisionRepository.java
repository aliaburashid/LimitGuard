package com.example.limitguard.repository;

import com.example.limitguard.model.ApprovalDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision, Long> {
    // checks if a final decision already exists for this credit request
    // A credit request cannot have multiple final approval decisions.
    boolean existsByCreditRequestId(Long creditRequestId);
}