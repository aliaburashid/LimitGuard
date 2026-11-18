package com.example.activityservice.client;

import com.example.activityservice.dto.AuthContextResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

// allows Activity Service to communicate with Auth Service
// to check whether a user is allowed to receive real-time notifications
@Component
public class AuthServiceClient {

    // Spring uses this HTTP client to send requests to Auth Service.
    private final RestClient restClient;

    // Auth uses this key to identify calls from Activity Service.
    private final String internalServiceKey;

    // Spring passes in the Auth URL and service key from application.properties.
    public AuthServiceClient(
            @Value("${auth-service.url}") String authServiceUrl,
            @Value("${internal-service-key}") String internalServiceKey) {
        this.internalServiceKey = internalServiceKey;

        // Limit how long Activity waits to connect to Auth or read its response.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        // Set the base URL for all requests to Auth Service.
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    // Auth validates the JWT and returns the user's current institution.
    public Long getCurrentInstitution(String authorization) {
        // An SSE connection must include a bearer token.
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorization.substring(7).isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        // Activity needs a configured key before it can call the internal endpoint.
        if (internalServiceKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }

        AuthContextResponse user;
        try {
            user = getAuthContext(authorization.substring(7));
        } catch (RestClientResponseException exception) {
            // Keep Auth's access errors so the browser can react to them.
            if (exception.getStatusCode().value() == 401) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
            if (exception.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        } catch (RestClientException exception) {
            // Let the browser reconnect when Auth is reachable again.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }

        // Only active users with an institution can subscribe to its updates.
        if (user == null || user.getUserId() == null || !"ACTIVE".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (user.getFinancialInstitutionId() == null || user.getFinancialInstitutionId() <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return user.getFinancialInstitutionId();
    }

    // Ask Auth for the current user and convert its JSON response into a Java object.
    private AuthContextResponse getAuthContext(String jwt) {
        return restClient.get()
                .uri("/api/auth/internal/me")

                // Forward the caller's JWT and identify Activity as a trusted service.
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("X-Internal-Service-Key", internalServiceKey)
                .retrieve()
                .body(AuthContextResponse.class);
    }
}
