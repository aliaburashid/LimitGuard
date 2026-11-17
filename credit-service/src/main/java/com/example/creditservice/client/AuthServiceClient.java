package com.example.creditservice.client;

import com.example.creditservice.dto.AuthContextResponse;
import com.example.creditservice.dto.InternalUserDisplayResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Handles HTTP communication between Credit Service and Auth Service
@Component
public class AuthServiceClient {

    // Spring's HTTP client, used to send requests to Auth Service
    private final RestClient restClient;

    // Secret key used to communicate securely with Auth Service
    @Value("${internal-service-key}")
    private String internalServiceKey;

    // Constructor injection: Spring creates this class automatically
    public AuthServiceClient(
            // This tells Spring:
            //- Look for auth-service.url in application.properties
            //- If it doesn't exist, use http://localhost:8081
            @Value("${auth-service.url:http://localhost:8081}") String authServiceUrl) {

        // This creates the HTTP client and tells it where Auth Service is located
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    // Retrieves the current user's authorization information from Auth Service
    // The JWT is forwarded so Auth Service knows which user is making the request
    public AuthContextResponse getAuthContext(String jwt) {

        // Send a GET request to Auth Service
        return restClient.get()

                // The endpoint that returns the user's current authorization details
                .uri("/api/auth/internal/me")

                // Forward the user's JWT in the Authorization header
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)

                // Identifies Credit Service as a trusted internal caller.
                .header("X-Internal-Service-Key", internalServiceKey)

                // Execute the request and read the response.
                .retrieve()

                // Convert the JSON response into our Lombok DTO
                .body(AuthContextResponse.class);
    }

    // Retrieves a user's display information from Auth Service.
    // Auth Service verifies the caller's JWT, internal key,
    // and financial institution before returning the information.
    public InternalUserDisplayResponse getUserDisplay(
            Long userId,
            String jwt) {

        return restClient.get()
                .uri("/api/auth/internal/users/{userId}/display", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("X-Internal-Service-Key", internalServiceKey)
                .retrieve()
                .body(InternalUserDisplayResponse.class);
    }

}
