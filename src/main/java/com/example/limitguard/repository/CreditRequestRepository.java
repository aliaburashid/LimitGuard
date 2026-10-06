package com.example.limitguard.repository;

import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.model.CreditRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CreditRequestRepository extends JpaRepository<CreditRequest, Long> {
    Page<CreditRequest> findByRequesterId(Long requesterId, Pageable pageable);
    // finds the credit request where the status = RESERVED and expiresAt < current time
    List<CreditRequest> findByStatusAndExpiresAtBefore(CreditRequestStatus status, LocalDateTime currentTime);
    // finds credit requests waiting for Risk Officer approval
    // pagination keeps the approval queue manageable when there are many requests
    // USED FOR: give me only credit requests whose status is PENDING_APPROVAL
    Page<CreditRequest> findByStatus(CreditRequestStatus status, Pageable pageable);
}