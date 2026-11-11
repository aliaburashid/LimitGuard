package com.example.authservice.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import java.util.ArrayList;
import java.util.List;

// builds only providers that have credentials and explicit account links
@Configuration
@EnableConfigurationProperties(SsoProperties.class)
public class SsoConfiguration {
    private final SsoProperties settings;

    public SsoConfiguration(SsoProperties settings) {
        this.settings = settings;
    }

    public boolean googleEnabled() {
        return settings.getGoogle().hasCredentials() && !settings.getGoogle().getDomains().isEmpty();
    }

    public boolean microsoftEnabled() {
        return settings.getMicrosoft().hasCredentials()
                && settings.getMicrosoft().getTenantId().matches("[a-fA-F0-9-]{36}");
    }

    public InMemoryClientRegistrationRepository registrations() {
        List<ClientRegistration> clients = new ArrayList<>();
        String callback = settings.getFrontendUrl() + "/api/auth/sso/callback/{registrationId}";
        if (googleEnabled()) {
            clients.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(settings.getGoogle().getClientId())
                    .clientSecret(settings.getGoogle().getClientSecret())
                    .scope("openid", "profile", "email")
                    .redirectUri(callback).build());
        }
        if (microsoftEnabled()) {
            String base = "https://login.microsoftonline.com/" + settings.getMicrosoft().getTenantId();
            clients.add(ClientRegistration.withRegistrationId("microsoft")
                    .clientName("Microsoft")
                    .clientId(settings.getMicrosoft().getClientId())
                    .clientSecret(settings.getMicrosoft().getClientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .scope("openid", "profile", "email")
                    .authorizationUri(base + "/oauth2/v2.0/authorize")
                    .tokenUri(base + "/oauth2/v2.0/token")
                    .jwkSetUri(base + "/discovery/v2.0/keys")
                    .issuerUri(base + "/v2.0")
                    .userNameAttributeName("sub")
                    .redirectUri(callback).build());
        }
        // an unconfigured installation still supports the existing email login
        return clients.isEmpty() ? null : new InMemoryClientRegistrationRepository(clients);
    }
}
