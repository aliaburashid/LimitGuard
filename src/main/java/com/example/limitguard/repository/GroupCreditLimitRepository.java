package com.example.limitguard.repository;

import com.example.limitguard.model.GroupCreditLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupCreditLimitRepository extends JpaRepository<GroupCreditLimit, Long> {

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
}
