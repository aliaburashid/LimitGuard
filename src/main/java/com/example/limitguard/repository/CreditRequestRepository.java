package com.example.limitguard.repository;

import com.example.limitguard.model.CreditRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface CreditRequestRepository extends JpaRepository<CreditRequest, Long> {
    Page<CreditRequest> findByRequesterId(Long requesterId, Pageable pageable);
}