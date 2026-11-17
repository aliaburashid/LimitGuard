package com.example.creditservice.repository;

import com.example.creditservice.model.CounterpartyFinancialProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CounterpartyFinancialProfileRepository extends JpaRepository<CounterpartyFinancialProfile, Long> {

    // finds the profile using its counterparty id
    Optional<CounterpartyFinancialProfile> findByCounterpartyId(Long counterpartyId);
}
