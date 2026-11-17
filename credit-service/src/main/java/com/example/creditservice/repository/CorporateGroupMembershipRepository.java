package com.example.creditservice.repository;

import com.example.creditservice.model.CorporateGroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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


    @Query("select m from CorporateGroupMembership m join m.counterparty c where " + CatalogScope.COUNTERPARTY)
    List<CorporateGroupMembership> findVisible(Long bank);

    @Query("select m from CorporateGroupMembership m join m.counterparty c where m.id = :id and "
        + CatalogScope.COUNTERPARTY)
    Optional<CorporateGroupMembership> findVisibleById(Long id, Long bank);

    @Query("select m from CorporateGroupMembership m join m.counterparty c where m.corporateGroup.id = :id and "
        + CatalogScope.COUNTERPARTY)
    List<CorporateGroupMembership> findVisibleByGroupId(Long id, Long bank);
}