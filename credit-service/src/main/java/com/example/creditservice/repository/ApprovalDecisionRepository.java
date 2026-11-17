package com.example.creditservice.repository;

import com.example.creditservice.model.ApprovalDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision, Long> {
    // checks if a final decision already exists for this credit request
    // A credit request cannot have multiple final approval decisions.
    boolean existsByCreditRequestId(Long creditRequestId);
}