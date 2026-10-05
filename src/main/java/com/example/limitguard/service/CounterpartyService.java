package com.example.limitguard.service;

import com.example.limitguard.dto.CounterpartyRequest;
import com.example.limitguard.dto.CounterpartyResponse;
import com.example.limitguard.enums.CounterpartyStatus;
import com.example.limitguard.exception.CounterpartyAlreadyExistsException;
import com.example.limitguard.model.AuditLog;
import com.example.limitguard.model.Counterparty;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.AuditLogRepository;
import com.example.limitguard.repository.CounterpartyRepository;
import com.example.limitguard.security.MyUserDetails;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CounterpartyService {

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    //---------------------------------------------------------------------------------

    // gets the user who is currently logged in
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

        // returns the User object stored inside MyUserDetails
        return userDetails.getUser();
    }

    // creates a new counterparty
    @Transactional
    public CounterpartyResponse createCounterparty(CounterpartyRequest counterpartyRequest) {

        // check if another counterparty has the same name
        if (counterpartyRepository.existsByName(counterpartyRequest.getName())) {
            throw new CounterpartyAlreadyExistsException("Counterparty name already exists");
        }

        // create a new counterparty object
        Counterparty counterparty = new Counterparty();
        // adds the name from the request
        counterparty.setName(counterpartyRequest.getName());
        // every new counterparty starts as ACTIVE
        counterparty.setStatus(CounterpartyStatus.ACTIVE);

        // saves the counterparty in the database
        Counterparty savedCounterparty = counterpartyRepository.save(counterparty);

        // gets the RISK_OFFICER who created the counterparty
        User currentUser = getCurrentLoggedInUser();

        // creates an audit log for the counterparty creation
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("COUNTERPARTY_CREATED");
        auditLog.setEntityType("COUNTERPARTY");
        auditLog.setEntityId(savedCounterparty.getId());
        auditLog.setDetails("Created counterparty: " + savedCounterparty.getName());
        auditLog.setActor(currentUser);

        // saves the audit log in the database
        auditLogRepository.save(auditLog);

        // creates the response with the saved counterparty information
        CounterpartyResponse counterpartyResponse =
                new CounterpartyResponse(
                        savedCounterparty.getId(),
                        savedCounterparty.getName(),
                        savedCounterparty.getStatus(),
                        savedCounterparty.getCreatedAt(),
                        savedCounterparty.getUpdatedAt()
                );

        // returns the counterparty response
        return counterpartyResponse;

    }
}
