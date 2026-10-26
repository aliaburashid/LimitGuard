package com.example.limitguard.repository;

import com.example.limitguard.model.CreditLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditLimitRepository extends JpaRepository<CreditLimit, Long> {

    @Override
    @EntityGraph(attributePaths = {"financialInstitution", "counterparty"})
    Optional<CreditLimit> findById(Long id);

    // checks if a credit limit already exists for the same
    // financial institution and counterparty combination
    boolean existsByFinancialInstitutionIdAndCounterpartyId(Long financialInstitutionId, Long counterpartyId);

    // finds credit limits for group counterparties belonging to the logged-in user's bank
    List<CreditLimit> findByFinancialInstitutionIdAndCounterpartyIdIn(
            Long financialInstitutionId,
            List<Long> counterpartyIds
    );
}