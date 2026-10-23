package com.example.limitguard.security;

import com.example.limitguard.model.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class InstitutionAccessService {

    // get the logged-in user's bank id
    public Long getCurrentInstitutionId() {

        // get the current logged-in user from spring security
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // deny access if the user is not logged in
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Institution access is required");
        }

        // check if the user uses the custom user details
        if (!(authentication.getPrincipal() instanceof MyUserDetails)) {
            throw new AccessDeniedException("Institution access is required");
        }

        // get the user's details
        MyUserDetails userDetails = (MyUserDetails) authentication.getPrincipal();

        // get the user object
        User user = userDetails.getUser();

        // deny access if the user has no bank
        if (user == null || user.getFinancialInstitution() == null) {
            throw new AccessDeniedException("Institution access is required");
        }

        // get the user's bank id
        Long institutionId = user.getFinancialInstitution().getId();

        // deny access if the bank id is missing
        if (institutionId == null) {
            throw new AccessDeniedException("Institution access is required");
        }

        return institutionId;
    }

    // check if the user belongs to the requested bank
    public void requireInstitution(Long institutionId) {

        // get the logged-in user's bank id
        Long currentInstitutionId = getCurrentInstitutionId();

        // deny access if the bank ids are different
        if (!currentInstitutionId.equals(institutionId)) {
            throw new AccessDeniedException(
                    "You do not have permission to access this institution's credit data"
            );
        }
    }
}