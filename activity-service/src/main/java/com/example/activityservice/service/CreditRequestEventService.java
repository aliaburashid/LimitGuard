package com.example.activityservice.service;

import com.example.activityservice.dto.CounterpartyStatusEvent;
import com.example.activityservice.dto.CreditLimitUpdatedEvent;
import com.example.activityservice.dto.CreditRequestStatusEvent;
import com.example.activityservice.dto.GroupCreditLimitUpdatedEvent;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Manages real-time connections between Activity Service and the frontend
@Service
public class CreditRequestEventService {

    // Stores all active SSE connections grouped by financial institution ID.
    // Long = institution ID, Set<SseEmitter> = connected browsers for that institution
    // ConcurrentHashMap allows multiple users to connect safely at the same time
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // Called by the controller when a user wants to receive real-time updates
    public SseEmitter subscribe(Long institutionId) throws IOException {

        // Create an SSE connection that stays open for up to 5 minutes
        // After reconnecting, the user's permissions are checked again by Auth Service
        SseEmitter emitter = new SseEmitter(300_000L);

        // Remove the connection when the browser disconnects normally
        emitter.onCompletion(() -> remove(institutionId, emitter));

        // Remove the connection if an error occurs
        emitter.onError(error -> remove(institutionId, emitter));

        // Remove and close the connection when the 5-minute timeout is reached
        emitter.onTimeout(() -> {
            remove(institutionId, emitter);
            emitter.complete();
        });

        // Send an initial SSE comment to establish the connection
        // This is not a credit request notification
        emitter.send(SseEmitter.event().comment("connected"));

        // Find the connections belonging to this institution
        // If the institution has no connections yet, create a new Set
        emitters.compute(institutionId, (id, connections) -> {

            if (connections == null) {
                // Create a thread-safe Set to store multiple browser connections
                connections = ConcurrentHashMap.newKeySet();
            }

            // Add the new browser connection to this institution's Set
            connections.add(emitter);

            // Save the updated Set in the Map
            return connections;
        });

        // Return the SSE connection to the controller, then to the frontend
        return emitter;
    }

    // Sends a credit request status update to users from the same institution
    public void publishStatusUpdate(Long institutionId, Long creditRequestId, String status) {

        // Create a DTO containing the credit request ID and its new status
        send(institutionId, "credit-request-status", new CreditRequestStatusEvent(creditRequestId, status));
    }

    // Send the counterparty update to browsers from the actor's institution.
    public void publishCounterpartyStatusUpdate(Long institutionId, Long counterpartyId, String status) {
        send(institutionId, "counterparty-status", new CounterpartyStatusEvent(counterpartyId, status));
    }

    // Send the old and new approved amounts to browsers from the facility's institution.
    public void publishCreditLimitUpdate(
            Long institutionId, Long creditLimitId, BigDecimal oldLimitAmount, BigDecimal newLimitAmount) {
        send(institutionId, "credit-limit-updated",
                new CreditLimitUpdatedEvent(creditLimitId, oldLimitAmount, newLimitAmount));
    }

    // Send the shared limit change to browsers from its owning institution.
    public void publishGroupCreditLimitUpdate(Long institutionId, Long groupCreditLimitId,
            Long corporateGroupId, BigDecimal oldLimitAmount, BigDecimal newLimitAmount) {
        send(institutionId, "group-credit-limit-updated",
                new GroupCreditLimitUpdatedEvent(groupCreditLimitId, corporateGroupId, oldLimitAmount, newLimitAmount));
    }

    private void send(Long institutionId, String eventName, Object event) {
        // Get all connected browsers for this institution
        // If no browsers are connected, use an empty Set
        for (SseEmitter emitter : emitters.getOrDefault(institutionId, Set.of())) {

            try {
                // Send an SSE event to the frontend.
                // name = event type that React listens for
                // data = DTO converted to JSON
                emitter.send(SseEmitter.event().name(eventName)
                        .data(event, MediaType.APPLICATION_JSON));

            } catch (IOException | IllegalStateException exception) {

                // If one browser disconnects, remove it without affecting other users
                remove(institutionId, emitter);
            }
        }
    }

    // Removes a disconnected or expired SSE connection
    private void remove(Long institutionId, SseEmitter emitter) {

        // Find the institution's connections, if they exist
        emitters.computeIfPresent(institutionId, (id, connections) -> {

            // Remove this browser's connection from the Set
            connections.remove(emitter);

            // If no connections remain, remove the institution from the Map
            // Otherwise, keep the remaining connections
            return connections.isEmpty() ? null : connections;
        });
    }
}
