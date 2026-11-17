package com.example.creditservice.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUserSecurity {

    private CurrentUserSecurity() {
    }

    public static AuthenticatedCreditUser getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedCreditUser user)) {
            throw new AccessDeniedException("Authentication required");
        }

        return user;
    }

    public static String getCurrentJwt() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedCreditUser)
                || !(authentication.getCredentials() instanceof String jwt)
                || jwt.isBlank()) {
            throw new AccessDeniedException("Valid JWT required");
        }

        return jwt;
    }
}
