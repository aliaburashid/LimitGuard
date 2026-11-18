package com.example.activityservice.client;

import com.example.activityservice.dto.NotificationRecipientResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Optional;

@Component
public class NotificationRecipientClient {

    private final RestClient restClient;
    private final String activityServiceKey;

    public NotificationRecipientClient(@Value("${auth-service.url}") String authServiceUrl,
            @Value("${activity.email.recipient-key}") String activityServiceKey) {
        this.activityServiceKey = activityServiceKey;

        // Limit how long the worker waits for Auth.
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public Optional<String> findRecipient(long requesterId, long financialInstitutionId) {
        if (activityServiceKey.isBlank()) {
            throw new RestClientException("Activity recipient key is not configured");
        }

        try {
            // Auth verifies the requester and institution using the worker's key.
            NotificationRecipientResponse recipient = restClient.get()
                    .uri("/api/auth/internal/notification-recipient?requesterId={requesterId}"
                            + "&financialInstitutionId={financialInstitutionId}", requesterId, financialInstitutionId)
                    .header("X-Activity-Service-Key", activityServiceKey)
                    .retrieve()
                    .body(NotificationRecipientResponse.class);
            if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
                throw new RestClientException("Auth returned no recipient address");
            }
            return Optional.of(recipient.getEmail());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }
}
