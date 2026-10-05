package com.example.limitguard.repository;

import com.example.limitguard.model.CreditLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditLimitRepository extends JpaRepository<CreditLimit, Long> {

    // checks if a credit limit already exists for the same
    // financial institution and counterparty combination
    boolean existsByFinancialInstitutionIdAndCounterpartyId(Long financialInstitutionId, Long counterpartyId);
}