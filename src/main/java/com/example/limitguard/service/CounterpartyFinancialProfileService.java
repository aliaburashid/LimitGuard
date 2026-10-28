package com.example.limitguard.service;

import com.example.limitguard.dto.CounterpartyFinancialProfileRequest;
import com.example.limitguard.dto.CounterpartyFinancialProfileResponse;
import com.example.limitguard.exception.CounterpartyNotFoundException;
import com.example.limitguard.model.AuditLog;
import com.example.limitguard.model.Counterparty;
import com.example.limitguard.model.CounterpartyFinancialProfile;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.AuditLogRepository;
import com.example.limitguard.repository.CounterpartyFinancialProfileRepository;
import com.example.limitguard.repository.CounterpartyRepository;
import com.example.limitguard.security.MyUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CounterpartyFinancialProfileService {

    private final CounterpartyRepository counterpartyRepository;
    private final CounterpartyFinancialProfileRepository profileRepository;
    private final AuditLogRepository auditLogRepository;

    // creates a profile or updates the existing profile for this counterparty
    @Transactional
    public CounterpartyFinancialProfileResponse saveProfile(
            Long counterpartyId, CounterpartyFinancialProfileRequest request) {

        // do not create financial records for an unknown counterparty
        Counterparty counterparty = counterpartyRepository.findById(counterpartyId)
                .orElseThrow(() -> new CounterpartyNotFoundException("Counterparty not found"));

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
        MyUserDetails details = (MyUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        User actor = details.getUser();
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(isNew ? "FINANCIAL_PROFILE_CREATED" : "FINANCIAL_PROFILE_UPDATED");
        auditLog.setEntityType("COUNTERPARTY_FINANCIAL_PROFILE");
        auditLog.setEntityId(saved.getId());
        auditLog.setDetails("Financial profile saved for counterparty id " + counterpartyId);
        auditLog.setActor(actor);
        auditLog.setFinancialInstitution(actor.getFinancialInstitution());
        auditLogRepository.save(auditLog);

        return toResponse(saved);
    }

    // returns the financial profile for one counterparty
    @Transactional(readOnly = true)
    public CounterpartyFinancialProfileResponse getProfile(Long counterpartyId) {
        if (!counterpartyRepository.existsById(counterpartyId)) {
            throw new CounterpartyNotFoundException("Counterparty not found");
        }

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
