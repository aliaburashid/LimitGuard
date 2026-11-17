package com.example.creditservice.service;

import com.example.creditservice.dto.CounterpartyFinancialProfileRequest;
import com.example.creditservice.dto.CounterpartyFinancialProfileResponse;
import com.example.creditservice.model.AuditLog;
import com.example.creditservice.model.Counterparty;
import com.example.creditservice.model.CounterpartyFinancialProfile;
import com.example.creditservice.repository.AuditLogRepository;
import com.example.creditservice.repository.CounterpartyFinancialProfileRepository;
import com.example.creditservice.security.AuthenticatedCreditUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.example.creditservice.security.CatalogAccessService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CounterpartyFinancialProfileService {

    private final CatalogAccessService catalogAccess;

    private final CounterpartyFinancialProfileRepository profileRepository;
    private final AuditLogRepository auditLogRepository;

    // creates a profile or updates the existing profile for this counterparty
    @Transactional
    public CounterpartyFinancialProfileResponse saveProfile(
            Long counterpartyId, CounterpartyFinancialProfileRequest request) {

        // do not create financial records for an unknown counterparty
        Counterparty counterparty = catalogAccess.requireWritableCounterparty(counterpartyId);

        // reusing the same profile prevents duplicate rows
        CounterpartyFinancialProfile profile = profileRepository.findByCounterpartyId(counterpartyId)
                .orElseGet(() -> {
                    CounterpartyFinancialProfile newProfile = new CounterpartyFinancialProfile();
                    newProfile.setCounterparty(counterparty);
                    return newProfile;
                });

        boolean isNew = profile.getId() == null;

        // copy only the validated financial inputs from the request
        profile.setReturnOnAssets(request.getReturnOnAssets());
        profile.setProfitMargin(request.getProfitMargin());
        profile.setFixedAssetTurnover(request.getFixedAssetTurnover());
        profile.setDebtToTotalCapitalPercent(request.getDebtToTotalCapitalPercent());
        profile.setInterestCoverage(request.getInterestCoverage());
        profile.setTotalDebtToEbitda(request.getTotalDebtToEbitda());
        profile.setFinancialYearEnd(request.getFinancialYearEnd());

        CounterpartyFinancialProfile saved = profileRepository.saveAndFlush(profile);

        // record the user who changed the financial information
        AuthenticatedCreditUser actor =
                (AuthenticatedCreditUser) SecurityContextHolder.getContext()
                        .getAuthentication().getPrincipal();
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(isNew ? "FINANCIAL_PROFILE_CREATED" : "FINANCIAL_PROFILE_UPDATED");
        auditLog.setEntityType("COUNTERPARTY_FINANCIAL_PROFILE");
        auditLog.setEntityId(saved.getId());
        auditLog.setDetails("Financial profile saved for counterparty id " + counterpartyId);
        auditLog.setActorId(actor.getUserId());
        auditLog.setFinancialInstitutionId(actor.getFinancialInstitutionId());
        auditLogRepository.save(auditLog);

        return toResponse(saved);
    }

    // returns the financial profile for one counterparty
    @Transactional(readOnly = true)
    public CounterpartyFinancialProfileResponse getProfile(Long counterpartyId) {
        catalogAccess.requireCounterparty(counterpartyId);
        return loadProfile(counterpartyId);
    }

    // Group assessment workers receive the bank ID from the authenticated request thread.
    @Transactional(readOnly = true)
    CounterpartyFinancialProfileResponse getProfileForAssessment(Long counterpartyId, Long bank) {
        catalogAccess.requireCounterparty(counterpartyId, bank);
        return loadProfile(counterpartyId);
    }

    private CounterpartyFinancialProfileResponse loadProfile(Long counterpartyId) {

        CounterpartyFinancialProfile profile = profileRepository.findByCounterpartyId(counterpartyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Financial profile not found"));
        return toResponse(profile);
    }

    // maps the database entity to a response without exposing jpa relationships
    private CounterpartyFinancialProfileResponse toResponse(CounterpartyFinancialProfile profile) {
        return new CounterpartyFinancialProfileResponse(
                profile.getId(),
                profile.getCounterparty().getId(),
                profile.getCounterparty().getName(),
                profile.getReturnOnAssets(),
                profile.getProfitMargin(),
                profile.getFixedAssetTurnover(),
                profile.getDebtToTotalCapitalPercent(),
                profile.getInterestCoverage(),
                profile.getTotalDebtToEbitda(),
                profile.getFinancialYearEnd(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
