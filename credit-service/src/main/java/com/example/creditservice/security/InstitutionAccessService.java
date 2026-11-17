package com.example.creditservice.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class InstitutionAccessService {

    // Get the logged-in user's bank ID
    public Long getCurrentInstitutionId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Institution access is required");
        }

        if (!(authentication.getPrincipal() instanceof AuthenticatedCreditUser)) {
            throw new AccessDeniedException("Institution access is required");
        }

        AuthenticatedCreditUser user =
                (AuthenticatedCreditUser) authentication.getPrincipal();

        Long institutionId = user.getFinancialInstitutionId();

        if (institutionId == null) {
            throw new AccessDeniedException("Institution access is required");
        }

        return institutionId;
    }

    // Prevent users from accessing another bank's data
    public void requireInstitution(Long institutionId) {
        Long currentInstitutionId = getCurrentInstitutionId();

        if (!currentInstitutionId.equals(institutionId)) {
            throw new AccessDeniedException(
                    "You do not have permission to access this institution's credit data"
            );
        }
    }
}
