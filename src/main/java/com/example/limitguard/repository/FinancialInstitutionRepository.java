package com.example.limitguard.repository;

import com.example.limitguard.model.FinancialInstitution;
import org.springframework.data.jpa.repository.JpaRepository;

// Allows us to access and manage financial institutions in the database
public interface FinancialInstitutionRepository extends JpaRepository<FinancialInstitution, Long> {

    // checks if a financial institution with this name already exists
    boolean existsByName(String name);
}
