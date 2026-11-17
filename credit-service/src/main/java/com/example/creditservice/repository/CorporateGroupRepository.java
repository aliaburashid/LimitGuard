package com.example.creditservice.repository;

import com.example.creditservice.model.CorporateGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CorporateGroupRepository extends JpaRepository<CorporateGroup, Long> {

    // checks whether a corporate group already has this name
    boolean existsByName(String name);

    @Query("select g from CorporateGroup g where g.id = :id and " + CatalogScope.GROUP)
    Optional<CorporateGroup> findVisibleById(Long id, Long bank);

    @Query("select g from CorporateGroup g where " + CatalogScope.GROUP)
    Page<CorporateGroup> findVisible(Long bank, Pageable pageable);

    @Query("""
        select count(g) > 0 from CorporateGroup g where g.id = :id and (
          exists (select 1 from GroupCreditLimit l where l.corporateGroup.id = g.id
            and l.financialInstitutionId <> :bank)
          or exists (select 1 from AuditLog a where a.entityType = 'CORPORATE_GROUP'
            and a.action = 'CORPORATE_GROUP_CREATED' and a.entityId = g.id
            and a.financialInstitutionId <> :bank)
        )
        """)
    boolean hasOtherBankRelationship(Long id, Long bank);
}
