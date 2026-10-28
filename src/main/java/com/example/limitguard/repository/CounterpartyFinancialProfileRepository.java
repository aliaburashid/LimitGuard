package com.example.limitguard.repository;

import com.example.limitguard.model.CounterpartyFinancialProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CounterpartyFinancialProfileRepository extends JpaRepository<CounterpartyFinancialProfile, Long> {

    // finds the profile using its counterparty id
    Optional<CounterpartyFinancialProfile> findByCounterpartyId(Long counterpartyId);
}
