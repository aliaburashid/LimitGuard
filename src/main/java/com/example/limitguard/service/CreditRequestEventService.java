package com.example.limitguard.service;

import com.example.limitguard.dto.CreditRequestStatusEvent;
import com.example.limitguard.enums.CreditRequestStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class CreditRequestEventService {

    // connection cleanup and event delivery can run on different threads
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // creates a new SSE connection for a client
    public SseEmitter subscribe() throws IOException {

        // 0L means keep the connection open without a timeout
        SseEmitter emitter = new SseEmitter(0L);

        // remove the connection when the client disconnects
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        // flush the response immediately so the browser can show a live connection
        emitter.send(SseEmitter.event().comment("connected"));
        emitters.add(emitter);

        return emitter;
    }

    // sends a credit request status update to connected clients
    public void publishStatusUpdate(Long creditRequestId, CreditRequestStatus status) {

        CreditRequestStatusEvent event =
                new CreditRequestStatusEvent(creditRequestId, status);

        for (SseEmitter emitter : emitters) {
            try {
                // send the new status event to each one
                emitter.send(SseEmitter.event()
                                .name("credit-request-status")
                                .data(event)
                );

                // a connection may complete before its cleanup callback runs
            } catch (IOException | IllegalStateException exception) {
                emitters.remove(emitter);
            }
        }
    }
}
