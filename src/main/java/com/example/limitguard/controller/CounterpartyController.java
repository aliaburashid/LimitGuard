package com.example.limitguard.controller;

import com.example.limitguard.dto.CounterpartyRequest;
import com.example.limitguard.dto.CounterpartyResponse;
import com.example.limitguard.service.CounterpartyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/counterparties")
public class CounterpartyController {

    @Autowired
    private CounterpartyService counterpartyService;

    // creates a new counterparty
    @PostMapping
    @PreAuthorize("hasRole('RISK_OFFICER')")
    public ResponseEntity<CounterpartyResponse> createCounterparty(
            // gets and validates the counterparty information from the request body
            @Valid @RequestBody CounterpartyRequest counterpartyRequest) {

        // sends the request information to the service to create the counterparty
        CounterpartyResponse counterpartyResponse = counterpartyService.createCounterparty(counterpartyRequest);
        // returns the created counterparty with 201 Created
        return new ResponseEntity<>(counterpartyResponse, HttpStatus.CREATED);
    }
}
