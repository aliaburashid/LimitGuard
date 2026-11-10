package com.example.limitguard.security;

import com.example.limitguard.dto.LoginResponse;
import com.example.limitguard.enums.UserStatus;
import com.example.limitguard.enums.FinancialInstitutionStatus;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;

// converts a verified work identity into the same JWT used by email login
@Service
public class SsoLoginService {
    static final String USER_ID = "limitguard.sso.user";
    static final String COMPLETED_AT = "limitguard.sso.completed";
    private final SsoProperties settings;
    private final UserRepository users;
    private final JWTUtils jwt;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public SsoLoginService(SsoProperties settings, UserRepository users, JWTUtils jwt) {
        this(settings, users, jwt, Clock.systemUTC());
    }

    // a separate clock lets the tests check expiry without waiting
    SsoLoginService(SsoProperties settings, UserRepository users, JWTUtils jwt, Clock clock) {
        this.settings = settings;
        this.users = users;
        this.jwt = jwt;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Long resolveUser(String provider, OidcUser identity) {
        SsoProperties.Provider configuration;
        if ("google".equals(provider)) {
            configuration = settings.getGoogle();
            // only verified Google Workspace accounts in approved domains are accepted
            if (!Boolean.TRUE.equals(identity.getEmailVerified())
                    || configuration.getDomains().stream().noneMatch(domain -> domain.equalsIgnoreCase(identity.getClaimAsString("hd")))) {
                throw denied();
            }
        } else if ("microsoft".equals(provider)) {
            configuration = settings.getMicrosoft();
            if (!configuration.getTenantId().equalsIgnoreCase(identity.getClaimAsString("tid"))) {
                throw denied();
            }
        } else {
            throw denied();
        }
        // Spring validates the provider token, issuer, audience, expiry and nonce
        // the immutable subject must also have an explicit local account mapping
        Long id = configuration.getAccounts().get(identity.getSubject());
        if (id == null) throw denied();
        return allowedUser(id).getId();
    }

    public void complete(HttpSession session, Long id) {
        session.setAttribute(USER_ID, id);
        session.setAttribute(COMPLETED_AT, clock.millis());
    }

    @Transactional(readOnly = true)
    public LoginResponse exchange(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) throw denied();
        Long id;
        Long completed;
        // only one request can consume the successful provider login
        synchronized (session) {
            id = (Long) session.getAttribute(USER_ID);
            completed = (Long) session.getAttribute(COMPLETED_AT);
            session.removeAttribute(USER_ID);
            session.removeAttribute(COMPLETED_AT);
        }
        session.invalidate();
        if (id == null || completed == null || clock.millis() - completed > 60_000) throw denied();
        // rechecks local access in case an admin changed it during the redirect
        return new LoginResponse(jwt.generateJwtToken(new MyUserDetails(allowedUser(id))));
    }

    private User allowedUser(Long id) {
        User user = users.findById(id).orElseThrow(this::denied);
        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()
                || user.getFinancialInstitution() == null
                || user.getFinancialInstitution().getStatus() != FinancialInstitutionStatus.ACTIVE) throw denied();
        return user;
    }

    private ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Work account sign-in could not be completed. Contact your administrator.");
    }
}
