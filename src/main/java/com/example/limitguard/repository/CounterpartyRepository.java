package com.example.limitguard.repository;

import com.example.limitguard.enums.CounterpartyStatus;
import com.example.limitguard.model.Counterparty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

    // checks if a counterparty already exists with this name
    boolean existsByName(String name);

    /*  searches for counterparties whose name contains the given text.
     * Containing: the counterparty name does not need to be an exact match.
     * for example, searching "Bahrain" can find "Bahrain Trading Company".
     *
     * IgnoreCase:makes the search case-insensitive
     * for example, "bahrain" can still find "Bahrain Trading Company".
     *
     * Pageable: controls pagination and sorting.
     * for example: page=0, size=10, sort=name,asc.
     *
     * Page<Counterparty>:
     * returns one page of counterparties along with pagination information
     * such as total results and total pages.
     */
    Page<Counterparty> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // checks whether another counterparty already uses this name
    // does someone other than the counterparty I'm currently updating have this name?
    boolean existsByNameAndIdNot(String name, Long id);

    // filters counterparties by status
    Page<Counterparty> findByStatus(
            CounterpartyStatus status,
            Pageable pageable
    );

    // searches by name and filters by status at the same time
    Page<Counterparty> findByNameContainingIgnoreCaseAndStatus(
            String name,
            CounterpartyStatus status,
            Pageable pageable
    );
}
