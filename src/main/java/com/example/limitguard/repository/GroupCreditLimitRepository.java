package com.example.limitguard.repository;

import com.example.limitguard.model.GroupCreditLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;


import java.util.Optional;

@Repository
public interface GroupCreditLimitRepository extends JpaRepository<GroupCreditLimit, Long> {

    // Locks the shared corporate group credit limit
    // Prevents two Relationship Managers from allocating
    // the same available group capacity simultaneously
    // g -> groupCreditLimit object
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GroupCreditLimit g WHERE g.id = :id")
    Optional<GroupCreditLimit> findWithLockById(Long id);

    // checks whether this bank already has a limit for this group
    boolean existsByFinancialInstitutionIdAndCorporateGroupId(
            Long financialInstitutionId,
            Long corporateGroupId
    );

    // finds this bank's credit limit for a specific corporate group
    Optional<GroupCreditLimit> findByFinancialInstitutionIdAndCorporateGroupId(
            Long financialInstitutionId,
            Long corporateGroupId
    );

    // finds a group credit limit by id only if it belongs to the logged-in user's bank
    Optional<GroupCreditLimit> findByIdAndFinancialInstitutionId(
            Long id,
            Long financialInstitutionId
    );

    // gets all group credit limits belonging to the bank with pagination and sorting
    Page<GroupCreditLimit> findByFinancialInstitutionId(
            Long financialInstitutionId,
            Pageable pageable
    );
}
