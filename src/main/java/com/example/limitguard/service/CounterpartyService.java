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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.limitguard.exception.CounterpartyNotFoundException;

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

    // gets one counterparty by its id
    public CounterpartyResponse getCounterpartyById(Long counterpartyId) {
        // findById searches the counterparties table using the counterparty id
        // it returns an Optional because the counterparty might not exist
        Counterparty counterparty = counterpartyRepository.findById(counterpartyId)
                // if the counterparty does not exist, throw an exception
                .orElseThrow(() ->
                        new CounterpartyNotFoundException("Counterparty not found"));

        // converts the Counterparty entity from the database
        // into a CounterpartyResponse DTO that we can send back to the user
        return new CounterpartyResponse(
                counterparty.getId(),
                counterparty.getName(),
                counterparty.getStatus(),
                counterparty.getCreatedAt(),
                counterparty.getUpdatedAt()
        );
    }

    // gets all counterparties or searches them by name
    // Pageable handles pagination and sorting
    public Page<CounterpartyResponse> getCounterparties(String name, Pageable pageable) {
        Page<Counterparty> counterparties;

        // if no name was provided, return all counterparties
        if (name == null || name.isBlank()) {
            counterparties = counterpartyRepository.findAll(pageable);
        } else {
            // otherwise search for counterparties containing the given name
            counterparties = counterpartyRepository.findByNameContainingIgnoreCase(name, pageable);
        }

        // converts each Counterparty inside the page into a CounterpartyResponse
        // map is used because we have multiple counterparties, not just one
        // Page.map() converts the contents but keeps the pagination information,
        // such as the page number, total elements and total pages
        return counterparties.map(counterparty ->
                new CounterpartyResponse(
                        counterparty.getId(),
                        counterparty.getName(),
                        counterparty.getStatus(),
                        counterparty.getCreatedAt(),
                        counterparty.getUpdatedAt()
                )
        );
    }
}

