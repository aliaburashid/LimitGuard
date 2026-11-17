package com.example.creditservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstitutionAccessServiceTest {

    private final InstitutionAccessService access = new InstitutionAccessService();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requiresAuthenticationAndAnInstitutionId() {
        assertThatThrownBy(access::getCurrentInstitutionId).isInstanceOf(AccessDeniedException.class);

        authenticate("RISK_OFFICER", null);
        assertThatThrownBy(access::getCurrentInstitutionId).isInstanceOf(AccessDeniedException.class);

        var principal = new AuthenticatedCreditUser(1L, "officer@example.test", "RISK_OFFICER", 5L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated(principal, null));
        assertThatThrownBy(access::getCurrentInstitutionId).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "RISK_OFFICER"})
    void limitsAccessToTheAuthenticatedInstitution(String role) {
        authenticate(role, 5L);

        assertThat(access.getCurrentInstitutionId()).isEqualTo(5L);
        access.requireInstitution(5L);
        assertThatThrownBy(() -> access.requireInstitution(6L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> access.requireInstitution(null)).isInstanceOf(AccessDeniedException.class);
    }

    private void authenticate(String role, Long institutionId) {
        var principal = new AuthenticatedCreditUser(1L, "officer@example.test", role, institutionId);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
