
package com.example.limitguard.service;

import com.example.limitguard.dto.CreditRequestStatusEvent;
import com.example.limitguard.enums.CounterpartyStatus;
import com.example.limitguard.enums.CreditRequestStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class CreditRequestEventService {

    // connection cleanup and event delivery can run on different threads
    // store SSE connections separately for each financial institution
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // creates a new SSE connection for a client
    public SseEmitter subscribe(Long institutionId) throws IOException {

        // 0L means keep the connection open without a timeout
        SseEmitter emitter = new SseEmitter(0L);

        // get the connections belonging to this bank
        List<SseEmitter> institutionEmitters = emitters.computeIfAbsent(
                institutionId,
                id -> new CopyOnWriteArrayList<>()
        );

        // remove the connection when the client disconnects
        emitter.onCompletion(() -> institutionEmitters.remove(emitter));
        emitter.onTimeout(() -> institutionEmitters.remove(emitter));
        emitter.onError(error -> institutionEmitters.remove(emitter));

        // register the connection so it can receive updates
        institutionEmitters.add(emitter);

        // send an initial message to establish the SSE stream
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException exception) {

            // Iif the connection fails, remove it from the list
            institutionEmitters.remove(emitter);

            // let Spring handle the failed connection
            throw exception;
        }

        return emitter;
    }

    // sends a credit request status update to connected clients
    public void publishStatusUpdate(Long institutionId, Long creditRequestId,
                                    CreditRequestStatus status) {

        CreditRequestStatusEvent event =
                new CreditRequestStatusEvent(creditRequestId, status);

        // get only the connections belonging to this bank
        List<SseEmitter> institutionEmitters = emitters.get(institutionId);

        // no connected users for this bank
        if (institutionEmitters == null) {
            return;
        }

        for (SseEmitter emitter : institutionEmitters) {
            try {
                // send the new status event to each one
                emitter.send(SseEmitter.event()
                        .name("credit-request-status")
                        .data(event)
                );

                // a connection may complete before its cleanup callback runs
            } catch (IOException | IllegalStateException exception) {
                institutionEmitters.remove(emitter);
            }
        }
    }


    // Sends real-time notifications when a counterparty status changes
    public void publishCounterpartyStatusUpdate(
            Long institutionId,
            Long counterpartyId,
            CounterpartyStatus status) {

        // STEP 1: Find all users currently connected through SSE
        // who belong to the same financial institution
        List<SseEmitter> connectedClients = emitters.get(institutionId);

        // If nobody from this bank is connected, there is nothing to send
        if (connectedClients == null || connectedClients.isEmpty()) {
            return;
        }

        // STEP 2: Create the information that will be sent to the frontend
        // The frontend can use this information to update the counterparty status
        Map<String, Object> eventData = Map.of(
                "counterpartyId", counterpartyId,
                "status", status.name()
        );

        // STEP 3: Send the status update to each connected user
        for (SseEmitter emitter : connectedClients) {

            try {

                // Use a separate event name so the frontend can distinguish
                // counterparty updates from credit request updates
                emitter.send(
                        SseEmitter.event()
                                .name("counterparty-status")
                                .data(eventData)
                );

            } catch (IOException | IllegalStateException exception) {

                // A connection may close before its cleanup callback runs
                // Remove the failed connection so it does not receive future events
                connectedClients.remove(emitter);
            }
        }
    }


    // Sends real-time notifications when a company's credit limit is updated
    public void publishCreditLimitUpdate(
            Long institutionId,
            Long creditLimitId,
            BigDecimal oldLimitAmount,
            BigDecimal newLimitAmount) {

        // STEP 1: Find all connected users belonging to the same bank
        // Reuse the existing SSE connections instead of creating new ones
        List<SseEmitter> connectedClients = emitters.get(institutionId);

        // If nobody from this bank is connected, there is nothing to send
        if (connectedClients == null || connectedClients.isEmpty()) {
            return;
        }

        // STEP 2: Create the information sent to the frontend
        // Include both the old and new amounts so users can see what changed
        Map<String, Object> eventData = Map.of(
                "creditLimitId", creditLimitId,
                "oldLimitAmount", oldLimitAmount,
                "newLimitAmount", newLimitAmount
        );

        // STEP 3: Send the update to each connected user
        for (SseEmitter emitter : connectedClients) {

            try {

                // Use a separate event name for credit limit changes
                // so the frontend can distinguish them from other notifications
                emitter.send(
                        SseEmitter.event()
                                .name("credit-limit-updated")
                                .data(eventData)
                );

            } catch (IOException | IllegalStateException exception) {

                // A connection may close before its cleanup callback runs
                // Remove the failed connection so it does not receive future events
                connectedClients.remove(emitter);
            }
        }
    }

    // Sends real-time notifications when a corporate group's credit limit is updated
    public void publishGroupCreditLimitUpdate(
            Long institutionId,
            Long groupCreditLimitId,
            Long corporateGroupId,
            BigDecimal oldLimitAmount,
            BigDecimal newLimitAmount) {

        // STEP 1: Find all connected users belonging to the same bank
        // Reuse the existing SSE connections instead of creating new ones
        List<SseEmitter> connectedClients = emitters.get(institutionId);

        // If nobody from this bank is connected, there is nothing to send
        if (connectedClients == null || connectedClients.isEmpty()) {
            return;
        }

        // STEP 2: Create the information sent to the frontend
        // Include the group ID and both amounts so users know what changed
        Map<String, Object> eventData = Map.of(
                "groupCreditLimitId", groupCreditLimitId,
                "corporateGroupId", corporateGroupId,
                "oldLimitAmount", oldLimitAmount,
                "newLimitAmount", newLimitAmount
        );

        // STEP 3: Send the update to each connected user
        for (SseEmitter emitter : connectedClients) {

            try {

                // Use a separate event name for corporate group limit changes
                // so the frontend can distinguish them from individual credit limit updates
                emitter.send(
                        SseEmitter.event()
                                .name("group-credit-limit-updated")
                                .data(eventData)
                );

            } catch (IOException | IllegalStateException exception) {

                // A connection may close before its cleanup callback runs
                // Remove the failed connection so it does not receive future events
                connectedClients.remove(emitter);
            }
        }
    }

}
