package com.example.limitguard.controller;

import com.example.limitguard.service.CreditRequestEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/credit-requests")
public class CreditRequestEventController {

    @Autowired
    private CreditRequestEventService creditRequestEventService;

    //----------------------------------------------------------

    // Opens an SSE connection so authenticated users can receive live updates
    // MediaType.TEXT_EVENT_STREAM_VALUE: normally a GET endpoint returns something and finishes
    // This endpoint is an SSE stream. Keep the connection available so events can be sent through it
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter subscribeToCreditRequestUpdates() {
        return creditRequestEventService.subscribe();
    }
}