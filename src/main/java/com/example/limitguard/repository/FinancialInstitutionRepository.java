package com.example.limitguard.repository;

import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.enums.FinancialInstitutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Allows us to access and manage financial institutions in the database
public interface FinancialInstitutionRepository extends JpaRepository<FinancialInstitution, Long> {

    // finds active institutions and keeps the dropdown ordered by name
    List<FinancialInstitution> findByStatusOrderByNameAscIdAsc(FinancialInstitutionStatus status);

    // checks if a financial institution with this name already exists
    boolean existsByName(String name);
}
