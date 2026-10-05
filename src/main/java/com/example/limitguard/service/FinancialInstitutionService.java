package com.example.limitguard.service;

import com.example.limitguard.dto.FinancialInstitutionRequest;
import com.example.limitguard.dto.FinancialInstitutionResponse;
import com.example.limitguard.exception.FinancialInstitutionAlreadyExistsException;
import com.example.limitguard.exception.FinancialInstitutionNotFoundException;
import com.example.limitguard.model.AuditLog;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.enums.FinancialInstitutionStatus;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.AuditLogRepository;
import com.example.limitguard.repository.FinancialInstitutionRepository;
import com.example.limitguard.security.MyUserDetails;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class FinancialInstitutionService {

    // gives this service access repo
    @Autowired
    private FinancialInstitutionRepository financialInstitutionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    //--------------------------------------------------------------------------------------------

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

        // Returns the User object stored inside MyUserDetails
        return userDetails.getUser();
    }

    // converts a FinancialInstitution entity into the response we send to the user
    private FinancialInstitutionResponse convertToResponse(FinancialInstitution financialInstitution) {
        return new FinancialInstitutionResponse(
                financialInstitution.getId(),
                financialInstitution.getName(),
                financialInstitution.getStatus(),
                financialInstitution.getCreatedAt(),
                financialInstitution.getUpdatedAt()
        );

    }

    // If something fails during the database transaction
    // Spring can roll the database changes back together
    @Transactional
    // creates a new financial institution
    public FinancialInstitutionResponse createFinancialInstitution(FinancialInstitutionRequest financialInstitutionRequest) {

        // checks if another financial institution already has this name
        if ( financialInstitutionRepository.existsByName(financialInstitutionRequest.getName())) {
            throw new FinancialInstitutionAlreadyExistsException("Financial institution name already exists");
        }

        // creates a new financial institution object
        FinancialInstitution financialInstitution = new FinancialInstitution();

        // takes the information from the request and adds it to the institution
        financialInstitution.setName(financialInstitutionRequest.getName());
        financialInstitution.setStatus(financialInstitutionRequest.getStatus());

        // saves the new financial institution in the database
        FinancialInstitution savedFinancialInstitution = financialInstitutionRepository.save(financialInstitution);

        // gets the admin who created the financial institution
        User currentUser = getCurrentLoggedInUser();

        // creates an audit log for the financial institution creation
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("FINANCIAL_INSTITUTION_CREATED");
        auditLog.setEntityType("FINANCIAL_INSTITUTION");
        auditLog.setEntityId(savedFinancialInstitution.getId());
        auditLog.setDetails(
                "Created financial institution: " + savedFinancialInstitution.getName()
        );
        auditLog.setActor(currentUser);

        // saves the audit log in the database
        auditLogRepository.save(auditLog);

        // converts the saved institution into a response DTO and returns it
        return convertToResponse(savedFinancialInstitution);
    }

    @Transactional
    // updates an existing financial institution
    public FinancialInstitutionResponse updateFinancialInstitution(Long institutionId, FinancialInstitutionRequest financialInstitutionRequest) {

        // finds the financial institution using its ID
        FinancialInstitution financialInstitution =
                financialInstitutionRepository.findById(institutionId)
                        .orElseThrow(() ->
                                new FinancialInstitutionNotFoundException("Financial institution not found"));

        // checks for a duplicate name only if the institution name is being changed
        if (!financialInstitution.getName().equals(financialInstitutionRequest.getName())
                && financialInstitutionRepository.existsByName(financialInstitutionRequest.getName())) {

            throw new FinancialInstitutionAlreadyExistsException("Financial institution name already exists");
        }

        // stores the old institution information before updating it
        String oldName = financialInstitution.getName();
        FinancialInstitutionStatus oldStatus = financialInstitution.getStatus();

        // updates the institution with the new information
        financialInstitution.setName(financialInstitutionRequest.getName());
        financialInstitution.setStatus(financialInstitutionRequest.getStatus());

        // saves the updated institution in the database
        FinancialInstitution updatedFinancialInstitution = financialInstitutionRepository.save(financialInstitution);

        // gets the admin who updated the financial institution
        User currentUser = getCurrentLoggedInUser();

        // creates an audit log for the financial institution update
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("FINANCIAL_INSTITUTION_UPDATED");
        auditLog.setEntityType("FINANCIAL_INSTITUTION");
        auditLog.setEntityId(updatedFinancialInstitution.getId());

        auditLog.setDetails(
                "Name: " + oldName + " -> " + updatedFinancialInstitution.getName()
                        + ", Status: " + oldStatus + " -> " + updatedFinancialInstitution.getStatus()
        );

        auditLog.setActor(currentUser);

        // saves the audit log in the database
        auditLogRepository.save(auditLog);

        // converts the updated institution into a response DTO and returns it
        return convertToResponse(updatedFinancialInstitution);
    }

}
