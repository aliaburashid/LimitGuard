package com.example.authservice.service;

import com.example.authservice.dto.FinancialInstitutionRequest;
import com.example.authservice.dto.FinancialInstitutionDirectoryResponse;
import com.example.authservice.dto.FinancialInstitutionResponse;
import com.example.authservice.exception.FinancialInstitutionAlreadyExistsException;
import com.example.authservice.exception.FinancialInstitutionNotFoundException;
import com.example.authservice.model.AuthAuditLog;
import com.example.authservice.model.FinancialInstitution;
import com.example.authservice.enums.FinancialInstitutionStatus;
import com.example.authservice.repository.AuthAuditLogRepository;
import com.example.authservice.repository.FinancialInstitutionRepository;
import com.example.authservice.security.MyUserDetails;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FinancialInstitutionService {

    // gives this service access repo
    @Autowired
    private FinancialInstitutionRepository financialInstitutionRepository;

    @Autowired
    private AuthAuditLogRepository authAuditLogRepository;

    // records changes under the institution being created or updated
    private void recordAuditLog(String action, FinancialInstitution institution, String details) {
        MyUserDetails actor = (MyUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        authAuditLogRepository.save(new AuthAuditLog(actor.getUser().getId(), action, "FINANCIAL_INSTITUTION",
                institution.getId(), institution.getId(), details));
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

    // gets the active institutions that users can select before logging in
    public List<FinancialInstitutionDirectoryResponse> getInstitutionDirectory() {

        // reads the existing institutions without changing any database records
        return financialInstitutionRepository
                .findByStatusOrderByNameAscIdAsc(FinancialInstitutionStatus.ACTIVE)
                .stream()
                // only returns the ID and name, not the full institution entity
                .map(institution -> new FinancialInstitutionDirectoryResponse(
                        institution.getId(), institution.getName()
                ))
                .toList();
    }

    // If something fails during the database transaction
    // Spring can roll the database changes back together
    @Transactional
    // creates a new financial institution
    public FinancialInstitutionResponse createFinancialInstitution(FinancialInstitutionRequest financialInstitutionRequest) {

        // checks if another financial institution already has this name
        if (financialInstitutionRepository.existsByName(financialInstitutionRequest.getName())) {
            throw new FinancialInstitutionAlreadyExistsException("Financial institution name already exists");
        }

        // creates a new financial institution object
        FinancialInstitution financialInstitution = new FinancialInstitution();

        // takes the information from the request and adds it to the institution
        financialInstitution.setName(financialInstitutionRequest.getName());
        financialInstitution.setStatus(financialInstitutionRequest.getStatus());

        // saves the new financial institution in the database
        FinancialInstitution savedFinancialInstitution = financialInstitutionRepository.save(financialInstitution);

        recordAuditLog("FINANCIAL_INSTITUTION_CREATED", savedFinancialInstitution,
                "Created financial institution: " + savedFinancialInstitution.getName()
                        + " | Status: " + savedFinancialInstitution.getStatus());

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

        recordAuditLog("FINANCIAL_INSTITUTION_UPDATED", updatedFinancialInstitution,
                "Name: " + oldName + " -> " + updatedFinancialInstitution.getName()
                        + ", Status: " + oldStatus + " -> " + updatedFinancialInstitution.getStatus());

        // converts the updated institution into a response DTO and returns it
        return convertToResponse(updatedFinancialInstitution);
    }
}
