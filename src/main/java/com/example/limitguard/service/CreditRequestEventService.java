
package com.example.limitguard.service;

import com.example.limitguard.dto.CreditRequestStatusEvent;
import com.example.limitguard.enums.CreditRequestStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

        // flush the response immediately so the browser can show a live connection
        emitter.send(SseEmitter.event().comment("connected"));
        institutionEmitters.add(emitter);

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
}
