package com.example.limitguard.repository;

import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.model.CreditRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreditRequestRepository extends JpaRepository<CreditRequest, Long> {

    @Override
    @EntityGraph(attributePaths = {"creditLimit.counterparty", "creditLimit.financialInstitution", "requester.financialInstitution"})
    Optional<CreditRequest> findById(Long id);

    // gets credit requests with their related company, bank and requester details
    // filters requests by bank, status, company and requester
    // makes sure pagination only counts matching requests from the same bank
    @EntityGraph(attributePaths = {"creditLimit.counterparty", "creditLimit.financialInstitution", "requester.financialInstitution"})
    @Query("""
            select r from CreditRequest r
            where r.creditLimit.financialInstitution.id = :institutionId
              and (:status is null or r.status = :status)
              and (:counterpartyId is null or r.creditLimit.counterparty.id = :counterpartyId)
              and (:requesterId is null or r.requester.id = :requesterId)
            """)
    Page<CreditRequest> searchInInstitution(
            @Param("institutionId") Long institutionId,
            @Param("status") CreditRequestStatus status,
            @Param("counterpartyId") Long counterpartyId,
            @Param("requesterId") Long requesterId,
            Pageable pageable
    );

    // scheduled expiry processes all institutions without a user session
    List<CreditRequest> findByStatusAndExpiresAtBefore(CreditRequestStatus status, LocalDateTime currentTime);

    // checks if a demo credit request already exists to avoid duplicates
    boolean existsBySeedKey(String seedKey);
}
