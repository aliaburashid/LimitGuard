package com.example.limitguard.repository;

import com.example.limitguard.model.CorporateGroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CorporateGroupMembershipRepository extends JpaRepository<CorporateGroupMembership, Long> {

    // finds all counterparties assigned to a specific corporate group
    List<CorporateGroupMembership> findByCorporateGroupId(Long corporateGroupId);

    // finds which group a counterparty belongs to
    Optional<CorporateGroupMembership> findByCounterpartyId(Long counterpartyId);

    // checks whether a counterparty is already assigned to a group
    boolean existsByCounterpartyId(Long counterpartyId);


}