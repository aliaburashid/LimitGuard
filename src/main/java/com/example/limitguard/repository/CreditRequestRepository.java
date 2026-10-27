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

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Repository
public interface CreditRequestRepository extends JpaRepository<CreditRequest, Long> {

    // redeclaring an existing method to add configuration
    @Override
    // tells JPA to load related information together with the credit request
    @EntityGraph(attributePaths = {"creditLimit.counterparty", "creditLimit.financialInstitution", "requester.financialInstitution"})
    Optional<CreditRequest> findById(Long id);

    // finds a credit request by ID and locks it during approval
    // prevents two Risk Officers from approving the same request at the same time
    // Lock: tells Spring Data JPA that we want to lock the database record when retrieving it.
    // PESSIMISTIC_WRITE: means only one transaction can hold the conflicting write lock on that credit request at a time
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CreditRequest> findWithLockById(Long id);

    // gets credit requests belonging to a specific bank
    // filters by status, company and requester if provided.
    // if a filter is null, it is ignored
    // uses pagination to return requests in smaller pages
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
