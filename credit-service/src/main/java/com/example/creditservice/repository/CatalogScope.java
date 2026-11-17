package com.example.creditservice.repository;

/** Catalog visibility comes from banking relationships or the existing creation record. */
final class CatalogScope {
    private CatalogScope() {}

    // The creation record allows a bank to see a new record before its first facility exists.
    // Legacy fixtures without a creation record must have a banking relationship to be visible.
    static final String COUNTERPARTY = """
        (exists (select 1 from CreditLimit l where l.counterparty.id = c.id
            and l.financialInstitutionId = :bank)
         or exists (select 1 from AuditLog a where a.entityType = 'COUNTERPARTY'
            and a.action = 'COUNTERPARTY_CREATED' and a.entityId = c.id
            and a.financialInstitutionId = :bank))
        """;

    static final String GROUP = """
        (exists (select 1 from GroupCreditLimit l where l.corporateGroup.id = g.id
            and l.financialInstitutionId = :bank)
         or exists (select 1 from AuditLog a where a.entityType = 'CORPORATE_GROUP'
            and a.action = 'CORPORATE_GROUP_CREATED' and a.entityId = g.id
            and a.financialInstitutionId = :bank)
         or exists (select 1 from CorporateGroupMembership m join m.counterparty c
            where m.corporateGroup.id = g.id and
        """ + COUNTERPARTY + "))";
}
