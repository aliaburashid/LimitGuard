package com.example.limitguard.controller;

import com.example.limitguard.dto.CreditRequestRequest;
import com.example.limitguard.dto.CreditRequestResponse;
import com.example.limitguard.service.CreditRequestService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/credit-requests")
public class CreditRequestController {

    @Autowired
    private CreditRequestService creditRequestService;

    //----------------------------------------------------------------

    // creates a new credit capacity request
    @PostMapping
    @PreAuthorize("hasRole('RELATIONSHIP_MANAGER')")
    public ResponseEntity<CreditRequestResponse> createCreditRequest(
            @Valid @RequestBody CreditRequestRequest creditRequestRequest) {
        CreditRequestResponse creditRequestResponse =
                creditRequestService.createCreditRequest(creditRequestRequest);
        return new ResponseEntity<>(creditRequestResponse, HttpStatus.CREATED
        );
    }

    // gets the credit requests submitted by the logged-in Relationship Manager
    @GetMapping("/my")
    @PreAuthorize("hasRole('RELATIONSHIP_MANAGER')")
    public ResponseEntity<Page<CreditRequestResponse>> getMyCreditRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        // decides whether the sorting should be ascending or descending
        Sort sort;

        if (direction.equalsIgnoreCase("ASC")) {
            sort = Sort.by(sortBy).ascending();
        } else {
            sort = Sort.by(sortBy).descending();
        }

        // creates the pagination and sorting settings
        Pageable pageable = PageRequest.of(page, size, sort);

        // gets only the logged-in users credit requests
        Page<CreditRequestResponse> creditRequests = creditRequestService.getMyCreditRequests(pageable);
        return new ResponseEntity<>(creditRequests, HttpStatus.OK);
    }

    // gets a credit request by ID
    @GetMapping("/{creditRequestId}")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER', 'RISK_OFFICER')")
    public ResponseEntity<CreditRequestResponse> getCreditRequestById(
            @PathVariable Long creditRequestId) {
        CreditRequestResponse creditRequestResponse =
                creditRequestService.getCreditRequestById(creditRequestId);
        return new ResponseEntity<>(creditRequestResponse, HttpStatus.OK);
    }

    // marks a reserved credit request as used
    @PatchMapping("/{creditRequestId}/use")
    @PreAuthorize("hasRole('RELATIONSHIP_MANAGER')")
    public ResponseEntity<CreditRequestResponse> markCreditRequestAsUsed(
            @PathVariable Long creditRequestId) {
        CreditRequestResponse creditRequestResponse =
                creditRequestService.markCreditRequestAsUsed(creditRequestId);
        return new ResponseEntity<>(creditRequestResponse, HttpStatus.OK);
    }
}