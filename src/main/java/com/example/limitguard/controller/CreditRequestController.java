package com.example.limitguard.controller;

import com.example.limitguard.dto.CreditRequestRequest;
import com.example.limitguard.dto.CreditRequestResponse;
import com.example.limitguard.service.CreditRequestService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
}