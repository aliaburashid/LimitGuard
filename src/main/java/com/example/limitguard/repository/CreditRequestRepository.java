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
    // filters credit requests by requester
    Page<CreditRequest> findByRequesterId(Long requesterId, Pageable pageable);

    // filters credit requests by counterparty
    Page<CreditRequest> findByCreditLimitCounterpartyId(Long counterpartyId, Pageable pageable);

    // finds the credit request where the status = RESERVED and expiresAt < current time
    List<CreditRequest> findByStatusAndExpiresAtBefore(CreditRequestStatus status, LocalDateTime currentTime);

    // USED FOR: filters credit requests by their status
    // finds credit requests waiting for Risk Officer approval
    // pagination keeps the approval queue manageable when there are many requests
    Page<CreditRequest> findByStatus(CreditRequestStatus status, Pageable pageable);

    // filters by status and counterparty
    Page<CreditRequest> findByStatusAndCreditLimitCounterpartyId(
            CreditRequestStatus status,
            Long counterpartyId,
            Pageable pageable
    );

    // filters by status and requester
    Page<CreditRequest> findByStatusAndRequesterId(
            CreditRequestStatus status,
            Long requesterId,
            Pageable pageable
    );

    // filters by counterparty and requester
    Page<CreditRequest> findByCreditLimitCounterpartyIdAndRequesterId(
            Long counterpartyId,
            Long requesterId,
            Pageable pageable
    );

    // filters by status, counterparty and requester
    Page<CreditRequest> findByStatusAndCreditLimitCounterpartyIdAndRequesterId(
            CreditRequestStatus status,
            Long counterpartyId,
            Long requesterId,
            Pageable pageable
    );
}