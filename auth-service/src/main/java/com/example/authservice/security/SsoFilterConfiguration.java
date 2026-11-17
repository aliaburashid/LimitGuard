package com.example.authservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

// provider redirects use a temporary session; banking APIs remain JWT-only
@Configuration
public class SsoFilterConfiguration {
    @Bean
    @Order(1)
    public SecurityFilterChain ssoFilterChain(HttpSecurity http, SsoConfiguration providers,
            SsoProperties settings, SsoLoginService login) throws Exception {
        URI frontend = URI.create(settings.getFrontendUrl());
        if (frontend.getHost() == null || frontend.getQuery() != null || frontend.getFragment() != null
                || !("https".equals(frontend.getScheme()) || ("http".equals(frontend.getScheme())
                && ("localhost".equals(frontend.getHost()) || "127.0.0.1".equals(frontend.getHost()))))) {
            throw new IllegalArgumentException("SSO frontend URL must be HTTPS or local development HTTP");
        }
        http.securityMatcher("/api/auth/sso/authorize/**", "/api/auth/sso/callback/**")
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // do not retain a provider principal as authentication for banking APIs
                .securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()));
        var clients = providers.registrations();
        if (clients == null) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll());
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .oauth2Login(oauth -> oauth
                            .clientRegistrationRepository(clients)
                            .authorizedClientRepository(new org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository())
                            .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/auth/sso/authorize"))
                            .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/auth/sso/callback/*"))
                            .successHandler((request, response, authentication) -> {
                                try {
                                    OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
                                    Long id = login.resolveUser(token.getAuthorizedClientRegistrationId(), (OidcUser) token.getPrincipal());
                                    // removes provider tokens and the authorization state before handoff
                                    clearSession(request);
                                    login.complete(request.getSession(true), id);
                                    SecurityContextHolder.clearContext();
                                    response.sendRedirect(settings.getFrontendUrl() + "/login#sso=complete");
                                } catch (org.springframework.web.server.ResponseStatusException rejected) {
                                    clearSession(request);
                                    SecurityContextHolder.clearContext();
                                    response.sendRedirect(settings.getFrontendUrl() + "/login#sso=error");
                                }
                            })
                            .failureHandler((request, response, exception) -> {
                                clearSession(request);
                                SecurityContextHolder.clearContext();
                                response.sendRedirect(settings.getFrontendUrl() + "/login#sso=error");
                            }));
        }
        return http.build();
    }

    private void clearSession(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}
