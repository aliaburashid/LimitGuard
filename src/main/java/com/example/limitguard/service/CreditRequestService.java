package com.example.limitguard.service;

import com.example.limitguard.dto.CreditRequestRequest;
import com.example.limitguard.dto.CreditRequestResponse;
import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.exception.CreditLimitNotFoundException;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.model.CreditRequest;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.repository.CreditRequestRepository;
import com.example.limitguard.security.MyUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CreditRequestService {

    @Autowired
    private CreditRequestRepository creditRequestRepository;

    @Autowired
    private CreditLimitRepository creditLimitRepository;

    //---------------------------------------------------------------------------

    // gets the currently logged-in user
    private User getCurrentLoggedInUser() {
        // ( SecurityContextHolder ): spring security keeps info and the currently authenticated/logged-in user here
        // (.getContext() ) : Gets the current security information.
        // (.getAuthentication() ) : info about who logged in
        // ( .getPrincipal() ): Gets the actual logged-in user's details.
        // gets the logged-in users details from Spring Security
        MyUserDetails userDetails = (MyUserDetails) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        return userDetails.getUser();
    }


    // submits a new credit capacity request
    @Transactional
    public CreditRequestResponse createCreditRequest(CreditRequestRequest creditRequestRequest) {

        // checks that the credit limit exists
        CreditLimit creditLimit = creditLimitRepository
                .findById(creditRequestRequest.getCreditLimitId())
                .orElseThrow(() ->
                        new CreditLimitNotFoundException("Credit limit not found"));

        // gets the requester from the logged-in user
        User requester = getCurrentLoggedInUser();

        // create a credit request object
        CreditRequest creditRequest = new CreditRequest();

        creditRequest.setAmount(creditRequestRequest.getAmount());
        creditRequest.setCreditLimit(creditLimit);
        creditRequest.setRequester(requester);

        // requests above 500,000 require Risk Officer approval
        if (creditRequestRequest.getAmount()
                .compareTo(new BigDecimal("500000")) > 0) {
            creditRequest.setStatus(CreditRequestStatus.PENDING_APPROVAL);
        } else {
            creditRequest.setStatus(CreditRequestStatus.RESERVED);
        }

        // save the credit request
        CreditRequest savedCreditRequest = creditRequestRepository.save(creditRequest);

        // return it in a DTO response
        return new CreditRequestResponse(
                savedCreditRequest.getId(),
                savedCreditRequest.getAmount(),
                savedCreditRequest.getStatus(),
                savedCreditRequest.getCreditLimit().getId(),
                savedCreditRequest.getCreditLimit().getCounterparty().getId(),
                savedCreditRequest.getRequester().getId(),
                savedCreditRequest.getExpiresAt(),
                savedCreditRequest.getCreatedAt(),
                savedCreditRequest.getUpdatedAt()
        );
    }
}