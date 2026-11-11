package com.example.authservice.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// keeps provider settings separate from passwords and existing banking data
@Getter
@Setter
@ConfigurationProperties(prefix = "limitguard.sso")
public class SsoProperties {
    // this must match the public frontend address used for provider callbacks
    private String frontendUrl = "http://127.0.0.1:5174";
    private Provider google = new Provider();
    private Provider microsoft = new Provider();

    @Getter
    @Setter
    public static class Provider {
        private String clientId = "";
        private String clientSecret = "";
        // Google Workspace domains allowed to use this application
        private List<String> domains = List.of();
        // one Microsoft organization, not the common or consumer tenant
        private String tenantId = "";
        // links a verified provider subject to an existing LimitGuard user ID
        // email addresses are not used to automatically link accounts
        private Map<String, Long> accounts = new HashMap<>();

        public boolean hasCredentials() {
            return !clientId.isBlank() && !clientSecret.isBlank() && !accounts.isEmpty();
        }
    }
}
