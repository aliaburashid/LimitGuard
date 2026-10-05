package com.example.limitguard.controller;

import com.example.limitguard.dto.CounterpartyRequest;
import com.example.limitguard.dto.CounterpartyResponse;
import com.example.limitguard.service.CounterpartyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

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

    // gets one counterparty by its id
    @GetMapping("/{counterpartyId}")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER', 'RISK_OFFICER', 'ADMIN')")
    public ResponseEntity<CounterpartyResponse> getCounterpartyById(
            @PathVariable Long counterpartyId) {

        CounterpartyResponse counterpartyResponse = counterpartyService.getCounterpartyById(counterpartyId);
        return new ResponseEntity<>(counterpartyResponse, HttpStatus.OK);
    }

    // gets all counterparties or searches counterparties by name
    // also supports pagination and sorting
    @GetMapping
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER', 'RISK_OFFICER', 'ADMIN')")
    public ResponseEntity<Page<CounterpartyResponse>> getCounterparties(
            // name is optional
            // example: /api/counterparties?name=Bahrain
            @RequestParam(required = false) String name,
            // default page = 0
            // default size = 10
            // default sorting = name ascending
            @PageableDefault(page = 0, size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<CounterpartyResponse> counterparties = counterpartyService.getCounterparties(name, pageable);
        return new ResponseEntity<>(counterparties, HttpStatus.OK);
    }

    @PutMapping("/{counterpartyId}")
    @PreAuthorize("hasRole('RISK_OFFICER')")
    public ResponseEntity<CounterpartyResponse> updateCounterparty(
            @PathVariable Long counterpartyId,
            @Valid @RequestBody CounterpartyRequest counterpartyRequest) {

        // sends the counterparty ID and new information to the service
        CounterpartyResponse counterpartyResponse =
                counterpartyService.updateCounterparty(counterpartyId, counterpartyRequest);

        // returns the updated counterparty with 200 OK
        return new ResponseEntity<>(counterpartyResponse, HttpStatus.OK);
    }

}
