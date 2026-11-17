package com.example.creditservice.security;

import com.example.creditservice.exception.CounterpartyNotFoundException;
import com.example.creditservice.exception.CorporateGroupNotFoundException;
import com.example.creditservice.model.Counterparty;
import com.example.creditservice.model.CorporateGroup;
import com.example.creditservice.repository.CounterpartyRepository;
import com.example.creditservice.repository.CorporateGroupRepository;
import com.example.creditservice.repository.CorporateGroupMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogAccessService {
    private final InstitutionAccessService institutions;
    private final CounterpartyRepository counterparties;
    private final CorporateGroupRepository groups;
    private final CorporateGroupMembershipRepository memberships;

    public Counterparty requireCounterparty(Long id) {
        return requireCounterparty(id, institutions.getCurrentInstitutionId());
    }

    public Counterparty requireCounterparty(Long id, Long bank) {
        return counterparties.findVisibleById(id, bank)
                .orElseThrow(() -> new CounterpartyNotFoundException("Counterparty not found"));
    }

    public CorporateGroup requireGroup(Long id) {
        return groups.findVisibleById(id, institutions.getCurrentInstitutionId())
                .orElseThrow(() -> new CorporateGroupNotFoundException("Corporate group not found"));
    }

    public Counterparty requireWritableCounterparty(Long id) {
        Counterparty company = requireCounterparty(id);
        if (hasOtherBankRelationship(id, institutions.getCurrentInstitutionId())) {
            throw new AccessDeniedException("Shared counterparty data cannot be edited by one institution");
        }
        return company;
    }

    public CorporateGroup requireWritableGroup(Long id) {
        CorporateGroup group = requireGroup(id);
        Long bank = institutions.getCurrentInstitutionId();
        if (groups.hasOtherBankRelationship(id, bank) || memberships.findByCorporateGroupId(id).stream()
                .anyMatch(member -> hasOtherBankRelationship(member.getCounterparty().getId(), bank))) {
            throw new AccessDeniedException("Shared corporate group data cannot be edited by one institution");
        }
        return group;
    }

    private boolean hasOtherBankRelationship(Long id, Long bank) {
        return counterparties.hasOtherBankFacility(id, bank) || counterparties.hasOtherBankCreator(id, bank);
    }
}
