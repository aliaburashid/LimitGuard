package com.example.limitguard.repository;

import com.example.limitguard.model.Counterparty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

    // checks if a counterparty already exists with this name
    boolean existsByName(String name);
}
