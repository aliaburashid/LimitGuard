package com.example.activityservice.controller;

import com.example.activityservice.client.AuthServiceClient;
import com.example.activityservice.service.CreditRequestEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

// Handles requests from the frontend to receive real-time credit request updates
@RestController
@RequiredArgsConstructor
public class CreditRequestEventController {

    // Communicates with Auth Service to check the logged-in user
    private final AuthServiceClient authServiceClient;

    // Manages SSE connections and sends credit request updates to users
    private final CreditRequestEventService creditRequestEventService;

    // GET endpoint that keeps a connection open to send real-time updates
    // TEXT_EVENT_STREAM_VALUE tells the browser to expect SSE events
    @GetMapping(value = "/api/credit-requests/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToCreditRequestUpdates(

            // Get the JWT from the Authorization header
            // required = false allows us to handle missing tokens ourselves
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization)
            throws IOException {

        // Ask Auth Service to validate the JWT and return the user's institution ID
        Long institutionId = authServiceClient.getCurrentInstitution(authorization);

        // Create an SSE connection for this institution and return it to the frontend
        return creditRequestEventService.subscribe(institutionId);
    }
}
