package com.example.creditservice.service;

import com.example.creditservice.client.AuthServiceClient;
import com.example.creditservice.dto.CreditRequestResponse;
import com.example.creditservice.dto.CreditRequestReviewResponse;
import com.example.creditservice.dto.InternalUserDisplayResponse;
import com.example.creditservice.security.CurrentUserSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CreditRequestDisplayService {

    private static final String UNAVAILABLE = "Unavailable";

    private final AuthServiceClient authServiceClient;

    // The controller calls this after the financial service transaction has completed.
    public CreditRequestResponse enrich(CreditRequestResponse response) {
        return applyDisplay(response, findDisplay(response.getRequesterId()));
    }

    public Page<CreditRequestResponse> enrich(Page<CreditRequestResponse> responses) {
        // Cache missing lookups as well as successful ones, for this page only.
        Map<Long, Optional<InternalUserDisplayResponse>> displayByRequester = new HashMap<>();
        return responses.map(response -> applyDisplay(response,
                displayByRequester.computeIfAbsent(response.getRequesterId(), this::findDisplay)));
    }

    public CreditRequestReviewResponse enrich(CreditRequestReviewResponse response) {
        var display = findDisplay(response.getRequesterId());
        response.setRequesterName(display.map(InternalUserDisplayResponse::getFullName).orElse(UNAVAILABLE));
        response.setFinancialInstitutionName(institutionName(display, response.getFinancialInstitutionId()));
        return response;
    }

    private CreditRequestResponse applyDisplay(CreditRequestResponse response,
                                               Optional<InternalUserDisplayResponse> display) {
        response.setRequesterName(display.map(InternalUserDisplayResponse::getFullName).orElse(UNAVAILABLE));
        response.setFinancialInstitutionName(institutionName(display, response.getFinancialInstitutionId()));
        return response;
    }

    private String institutionName(Optional<InternalUserDisplayResponse> display, Long institutionId) {
        return display.filter(actor -> Objects.equals(institutionId, actor.getFinancialInstitutionId()))
                .map(InternalUserDisplayResponse::getFinancialInstitutionName)
                .orElse(UNAVAILABLE);
    }

    private Optional<InternalUserDisplayResponse> findDisplay(Long requesterId) {
        String jwt = CurrentUserSecurity.getCurrentJwt();
        try {
            return Optional.ofNullable(authServiceClient.getUserDisplay(requesterId, jwt))
                    .filter(display -> Objects.equals(requesterId, display.getUserId()));
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }
}
