package com.example.limitguard.security;

import com.example.limitguard.enums.FinancialInstitutionStatus;
import com.example.limitguard.enums.UserStatus;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// uses fake accounts; no real users or banking records are changed
class SsoLoginServiceTest {
    private final SsoProperties settings = new SsoProperties();
    private final UserRepository users = mock(UserRepository.class);
    private final JWTUtils jwt = mock(JWTUtils.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC);
    private final SsoLoginService login = new SsoLoginService(settings, users, jwt, clock);
    private User user;

    @BeforeEach
    void setUp() {
        FinancialInstitution institution = new FinancialInstitution();
        institution.setStatus(FinancialInstitutionStatus.ACTIVE);
        user = new User();
        user.setId(7L);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setFinancialInstitution(institution);
        settings.getGoogle().setDomains(List.of("example.test"));
        settings.getGoogle().setAccounts(Map.of("google-subject", 7L));
        settings.getMicrosoft().setTenantId("organization-tenant");
        settings.getMicrosoft().setAccounts(Map.of("microsoft-subject", 7L));
    }

    private OidcUser google(String subject, String domain, boolean verified) {
        OidcUser identity = mock(OidcUser.class);
        when(identity.getEmailVerified()).thenReturn(verified);
        when(identity.getClaimAsString("hd")).thenReturn(domain);
        when(identity.getSubject()).thenReturn(subject);
        return identity;
    }

    @Test
    void acceptsOnlyExplicitlyLinkedWorkspaceAccounts() {
        when(users.findById(7L)).thenReturn(Optional.of(user));
        assertEquals(7L, login.resolveUser("google", google("google-subject", "example.test", true)));
        verify(users, never()).findByEmail(anyString());
        verify(users, never()).save(any());
    }

    @Test
    void rejectsUnknownSubjectsAndUnapprovedDomains() {
        assertThrows(ResponseStatusException.class, () -> login.resolveUser("google", google("unlinked", "example.test", true)));
        assertThrows(ResponseStatusException.class, () -> login.resolveUser("google", google("google-subject", "other.test", true)));
        assertThrows(ResponseStatusException.class, () -> login.resolveUser("google", google("google-subject", "example.test", false)));
        verifyNoInteractions(users, jwt);
    }

    @Test
    void requiresTheConfiguredMicrosoftTenantAndSubject() {
        OidcUser identity = mock(OidcUser.class);
        when(identity.getClaimAsString("tid")).thenReturn("other-tenant");
        assertThrows(ResponseStatusException.class, () -> login.resolveUser("microsoft", identity));
        when(identity.getClaimAsString("tid")).thenReturn("organization-tenant");
        when(identity.getSubject()).thenReturn("microsoft-subject");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        assertEquals(7L, login.resolveUser("microsoft", identity));
    }

    @Test
    void rejectsDisabledUsersAndInactiveInstitutions() {
        when(users.findById(7L)).thenReturn(Optional.of(user));
        user.setStatus(UserStatus.DEACTIVATED);
        assertThrows(ResponseStatusException.class, () -> login.resolveUser("google", google("google-subject", "example.test", true)));
        user.setStatus(UserStatus.ACTIVE);
        user.getFinancialInstitution().setStatus(FinancialInstitutionStatus.INACTIVE);
        assertThrows(ResponseStatusException.class, () -> login.resolveUser("google", google("google-subject", "example.test", true)));
    }

    @Test
    void exchangesSuccessfulLoginOnlyOnce() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        login.complete(request.getSession(), 7L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(jwt.generateJwtToken(any())).thenReturn("test-only-jwt");
        assertEquals("test-only-jwt", login.exchange(request).getMessage());
        assertThrows(ResponseStatusException.class, () -> login.exchange(request));
        verify(jwt, times(1)).generateJwtToken(any());
    }

    @Test
    void rejectsExpiredOrMissingLoginSessions() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThrows(ResponseStatusException.class, () -> login.exchange(request));
        login.complete(request.getSession(), 7L);
        request.getSession().setAttribute(SsoLoginService.COMPLETED_AT, clock.millis() - 60_001);
        assertThrows(ResponseStatusException.class, () -> login.exchange(request));
        verifyNoInteractions(users, jwt);
    }
}
