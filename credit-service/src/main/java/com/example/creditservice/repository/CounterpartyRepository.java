package com.example.creditservice.repository;

import com.example.creditservice.enums.CounterpartyStatus;
import com.example.creditservice.model.Counterparty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

    // checks if a counterparty already exists with this name
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    @Query("select c from Counterparty c where c.id = :id and " + CatalogScope.COUNTERPARTY)
    Optional<Counterparty> findVisibleById(Long id, Long bank);

    @Query("""
        select c from Counterparty c where (:status is null or c.status = :status)
        and lower(c.name) like lower(:pattern) escape '!'
        and
        """ + CatalogScope.COUNTERPARTY)
    Page<Counterparty> findVisible(Long bank, String pattern, CounterpartyStatus status, Pageable pageable);

    @Query("select count(c) > 0 from Counterparty c where c.id = :id and "
        + "exists (select 1 from CreditLimit l where l.counterparty.id = c.id and l.financialInstitutionId <> :bank)")
    boolean hasOtherBankFacility(Long id, Long bank);

    @Query("""
        select count(a) > 0 from AuditLog a where a.entityType = 'COUNTERPARTY'
        and a.action = 'COUNTERPARTY_CREATED' and a.entityId = :id
        and a.financialInstitutionId <> :bank
        """)
    boolean hasOtherBankCreator(Long id, Long bank);
}
