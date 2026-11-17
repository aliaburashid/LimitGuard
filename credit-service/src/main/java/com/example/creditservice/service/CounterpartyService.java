package com.example.creditservice.service;

import com.example.creditservice.dto.CounterpartyRequest;
import com.example.creditservice.dto.CounterpartyResponse;
import com.example.creditservice.dto.CounterpartyStatusRequest;
import com.example.creditservice.enums.CounterpartyStatus;
import com.example.creditservice.exception.CounterpartyAlreadyExistsException;
import com.example.creditservice.model.AuditLog;
import com.example.creditservice.model.Counterparty;
import com.example.creditservice.repository.AuditLogRepository;
import com.example.creditservice.repository.CounterpartyRepository;
import com.example.creditservice.security.AuthenticatedCreditUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.example.creditservice.security.CatalogAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.creditservice.security.InstitutionAccessService;

@Service
public class CounterpartyService {

    @Autowired
    private CatalogAccessService catalogAccess;

    @Autowired
    private InstitutionAccessService institutionAccessService;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationOutboxService notificationOutboxService;

    //---------------------------------------------------------------------------------

    // gets the user who is currently logged in
    private AuthenticatedCreditUser getCurrentLoggedInUser() {
        return (AuthenticatedCreditUser) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }

    // records counterparty changes in the audit log without repeating the same code
    private void recordAuditLog(String action, Counterparty counterparty, String details, AuthenticatedCreditUser actor) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setEntityType("COUNTERPARTY");
        auditLog.setEntityId(counterparty.getId());
        auditLog.setDetails(details);
        auditLog.setActorId(actor.getUserId());
        auditLog.setFinancialInstitutionId(actor.getFinancialInstitutionId());
        auditLogRepository.save(auditLog);
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
        AuthenticatedCreditUser currentUser = getCurrentLoggedInUser();

        // creates an audit log for the counterparty creation
        // save which bank performed this action
        // saves the audit log in the database
        recordAuditLog(
                "COUNTERPARTY_CREATED", savedCounterparty,
                "Created counterparty: " + savedCounterparty.getName(), currentUser
        );

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
        Counterparty counterparty = catalogAccess.requireCounterparty(counterpartyId);

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


    // gets all counterparties or searches/filters them by name and status
    // Pageable handles pagination and sorting
    public Page<CounterpartyResponse> getCounterparties(
            String name,
            CounterpartyStatus status,
            Pageable pageable) {

        String search = name == null || name.isBlank() ? "" : name;
        String pattern = "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Page<Counterparty> counterparties = counterpartyRepository.findVisible(
                institutionAccessService.getCurrentInstitutionId(), pattern, status, pageable);

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


    // updates counterparty
    @Transactional
    public CounterpartyResponse updateCounterparty(Long counterpartyId, CounterpartyRequest counterpartyRequest) {

        // finds the counterparty by id
        Counterparty counterparty = catalogAccess.requireWritableCounterparty(counterpartyId);

        // checks if another counterparty already has the requested name
        // IdNot excludes the counterparty that we are currently updating
        if (counterpartyRepository.existsByNameAndIdNot(counterpartyRequest.getName(), counterpartyId)) {
            throw new CounterpartyAlreadyExistsException("Counterparty already exists");
        }

        // updates the permitted counterparty information
        // status is NOT changed here because status management belongs to LG-25
        counterparty.setName(counterpartyRequest.getName());

        // saves the changes to PostgreSQL
        Counterparty updatedCounterparty = counterpartyRepository.save(counterparty);

        // gets the currently logged-in Risk Officer
        AuthenticatedCreditUser currentUser = getCurrentLoggedInUser();

        // records who updated the counterparty
        // save which bank updated the counterparty
        recordAuditLog(
                "COUNTERPARTY_UPDATED", updatedCounterparty,
                "Updated counterparty: " + updatedCounterparty.getName(), currentUser
        );

        // converts the Counterparty entity into the response DTO
        return new CounterpartyResponse(
                updatedCounterparty.getId(),
                updatedCounterparty.getName(),
                updatedCounterparty.getStatus(),
                updatedCounterparty.getCreatedAt(),
                updatedCounterparty.getUpdatedAt()
        );
    }


    // updates counterparty status
    @Transactional
    public CounterpartyResponse updateCounterpartyStatus(
            Long counterpartyId,
            CounterpartyStatusRequest statusRequest) {

        // finds the counterparty by id
        Counterparty counterparty = catalogAccess.requireWritableCounterparty(counterpartyId);

        // saves the current status before changing it
        // so we can record the old and new status in the audit log
        CounterpartyStatus oldStatus = counterparty.getStatus();

        // changes the counterparty status
        counterparty.setStatus(statusRequest.getStatus());

        // saves the status change to PostgreSQL
        Counterparty updatedCounterparty = counterpartyRepository.save(counterparty);

        // gets the currently logged-in Risk Officer
        AuthenticatedCreditUser currentUser = getCurrentLoggedInUser();

        // records the status change
        // save which bank changed the counterparty status
        recordAuditLog(
                "COUNTERPARTY_STATUS_UPDATED",
                updatedCounterparty,
                "Counterparty status changed from " + oldStatus
                        + " to " + updatedCounterparty.getStatus(),
                currentUser
        );

        // Save the notification in the same transaction as the update and audit.
        notificationOutboxService.recordCounterpartyStatusEvent(
                currentUser.getFinancialInstitutionId(),
                updatedCounterparty.getId(),
                updatedCounterparty.getStatus());

        return new CounterpartyResponse(
                updatedCounterparty.getId(),
                updatedCounterparty.getName(),
                updatedCounterparty.getStatus(),
                updatedCounterparty.getCreatedAt(),
                updatedCounterparty.getUpdatedAt()
        );
    }


}
