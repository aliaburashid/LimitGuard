package com.example.limitguard.service;

import com.example.limitguard.dto.CreditRequestStatusEvent;
import com.example.limitguard.enums.CreditRequestStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class CreditRequestEventService {

    // Stores the clients that are currently listening for credit request updates
    private final List<SseEmitter> emitters = new ArrayList<>();

    // Creates a new SSE connection for a client
    public SseEmitter subscribe() {

        // 0L means keep the connection open without a timeout
        SseEmitter emitter = new SseEmitter(0L);

        emitters.add(emitter);

        // Remove the connection when the client disconnects
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        return emitter;
    }

    // Sends a credit request status update to all connected clients
    public void publishStatusUpdate(Long creditRequestId, CreditRequestStatus status) {

        // creating an object from the class
        CreditRequestStatusEvent event =
                new CreditRequestStatusEvent(creditRequestId, status);

        // Keep track of disconnected clients
        List<SseEmitter> disconnectedEmitters = new ArrayList<>();

        // Go through every client that is currently listening
        for (SseEmitter emitter : emitters) {
            try {
                // send the new status event to each one
                emitter.send(SseEmitter.event()
                                .name("credit-request-status")
                                .data(event)
                );

                // if one client has disconnected, remember it so we can remove it
            } catch (IOException exception) {
                disconnectedEmitters.add(emitter);
            }
        }

        // Remove clients that are no longer connected
        emitters.removeAll(disconnectedEmitters);
    }
}