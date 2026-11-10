package com.example.limitguard.service;

import com.example.limitguard.exception.GroupCreditLimitNotFoundException;
import com.example.limitguard.model.*;
import com.example.limitguard.repository.CorporateGroupMembershipRepository;
import com.example.limitguard.repository.CorporateGroupRepository;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.repository.GroupCreditLimitRepository;
import com.example.limitguard.security.InstitutionAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupExposureBreakdownTest {

    @Mock InstitutionAccessService institutionAccess;
    @Mock GroupCreditLimitRepository groupLimits;
    @Mock CorporateGroupRepository groups;
    @Mock CorporateGroupMembershipRepository memberships;
    @Mock CreditLimitRepository creditLimits;
    @InjectMocks GroupCreditLimitService service;

    @Test
    void separatesSharedLimitFromMemberFacilitiesInTheCurrentInstitution() {
        when(institutionAccess.getCurrentInstitutionId()).thenReturn(3L);
        GroupCreditLimit shared = new GroupCreditLimit();
        shared.setAmount(new BigDecimal("1000"));
        when(groupLimits.findByFinancialInstitutionIdAndCorporateGroupId(3L, 4L)).thenReturn(Optional.of(shared));
        when(groups.findById(4L)).thenReturn(Optional.of(new CorporateGroup()));

        Counterparty first = new Counterparty();
        first.setId(10L);
        Counterparty second = new Counterparty();
        second.setId(11L);
        CorporateGroupMembership firstMember = new CorporateGroupMembership();
        firstMember.setCounterparty(first);
        CorporateGroupMembership secondMember = new CorporateGroupMembership();
        secondMember.setCounterparty(second);
        when(memberships.findByCorporateGroupId(4L)).thenReturn(List.of(firstMember, secondMember));
        when(creditLimits.findByFinancialInstitutionIdAndCounterpartyIdIn(3L, List.of(10L, 11L)))
                .thenReturn(List.of(
                        facility(50L, first, "800", "150", "75"),
                        facility(51L, second, "900", "225", "125")
                ));

        var exposure = service.getGroupExposureBreakdown(4L);
        assertEquals(new BigDecimal("1000"), exposure.limitAmount());
        assertEquals(new BigDecimal("375"), exposure.usedAmount());
        assertEquals(new BigDecimal("200"), exposure.reservedAmount());
        assertEquals(new BigDecimal("425"), exposure.availableHeadroom());
        assertEquals(List.of(50L, 51L), exposure.facilities().stream().map(facility -> facility.getCreditLimitId()).toList());
        assertEquals(new BigDecimal("575"), exposure.facilities().get(0).getAvailableHeadroom());
    }

    @Test
    void doesNotReadFacilitiesWhenTheInstitutionHasNoSharedLimit() {
        when(institutionAccess.getCurrentInstitutionId()).thenReturn(8L);
        when(groupLimits.findByFinancialInstitutionIdAndCorporateGroupId(8L, 4L)).thenReturn(Optional.empty());

        assertThrows(GroupCreditLimitNotFoundException.class, () -> service.getGroupExposureBreakdown(4L));
        verifyNoInteractions(groups, memberships, creditLimits);
    }

    private CreditLimit facility(Long id, Counterparty company, String limit, String used, String reserved) {
        CreditLimit facility = new CreditLimit();
        facility.setId(id);
        facility.setCounterparty(company);
        facility.setLimitAmount(new BigDecimal(limit));
        facility.setUsedAmount(new BigDecimal(used));
        facility.setReservedAmount(new BigDecimal(reserved));
        return facility;
    }
}
