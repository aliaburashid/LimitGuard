package com.example.limitguard.service;

import com.example.limitguard.dto.CorporateGroupRequest;
import com.example.limitguard.dto.CorporateGroupResponse;
import com.example.limitguard.exception.CorporateGroupAlreadyExistsException;
import com.example.limitguard.exception.CorporateGroupNotFoundException;
import com.example.limitguard.exception.InvalidCorporateGroupException;
import com.example.limitguard.model.CorporateGroup;
import com.example.limitguard.repository.CorporateGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CorporateGroupService {

    // gives this service access to the corporate group repository
    @Autowired
    private CorporateGroupRepository corporateGroupRepository;

    //-----------------------------------------------------------------

    // converts a CorporateGroup entity into a response DTO
    private CorporateGroupResponse convertToResponse(CorporateGroup group) {
        return new CorporateGroupResponse(
                group.getId(),
                group.getName(),
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }

    // creates a new corporate group
    public CorporateGroupResponse createGroup(CorporateGroupRequest request) {

        // checks if the group name is empty
        if (request == null || request.getName() == null
                || request.getName().isBlank()) {
            throw new InvalidCorporateGroupException(
                    "Corporate group name is required"
            );
        }

        // checks if another corporate group already has this name
        if (corporateGroupRepository.existsByName(request.getName())) {
            throw new CorporateGroupAlreadyExistsException(
                    "Corporate group already exists with name: " + request.getName()
            );
        }

        // creates a new corporate group entity
        CorporateGroup group = new CorporateGroup();
        group.setName(request.getName());

        // saves the group in the database
        CorporateGroup savedGroup = corporateGroupRepository.save(group);

        // converts the saved entity into a response DTO
        return convertToResponse(savedGroup);
    }

    // retrieves all corporate groups
    public List<CorporateGroupResponse> getAllGroups() {

        // gets all corporate groups from the database
        List<CorporateGroup> groups = corporateGroupRepository.findAll();

        // converts each corporate group into a response DTO
        return groups.stream()
                .map(this::convertToResponse)
                .toList();
    }

    // retrieves a corporate group using its ID
    public CorporateGroupResponse getGroupById(Long id) {

        // finds the group or throws an exception if it does not exist
        CorporateGroup group = corporateGroupRepository.findById(id)
                .orElseThrow(() -> new CorporateGroupNotFoundException(
                        "Corporate group not found with ID: " + id
                ));

        // converts the entity into a response DTO
        return convertToResponse(group);
    }

    // updates an existing corporate group
    public CorporateGroupResponse updateGroup(Long id, CorporateGroupRequest request) {

        // finds the existing corporate group
        CorporateGroup group = corporateGroupRepository.findById(id)
                .orElseThrow(() -> new CorporateGroupNotFoundException(
                        "Corporate group not found with ID: " + id
                ));

        // checks if the new name is empty
        if (request == null || request.getName() == null
                || request.getName().isBlank()) {
            throw new InvalidCorporateGroupException(
                    "Corporate group name is required"
            );
        }

        // checks if another group already has the new name
        if (!group.getName().equals(request.getName())
                && corporateGroupRepository.existsByName(request.getName())) {
            throw new CorporateGroupAlreadyExistsException(
                    "Corporate group already exists with name: " + request.getName()
            );
        }

        // updates the corporate group name
        group.setName(request.getName());

        // saves the updated group in the database
        CorporateGroup updatedGroup = corporateGroupRepository.save(group);

        // converts the updated entity into a response DTO
        return convertToResponse(updatedGroup);
    }

    // deletes a corporate group
    public void deleteGroup(Long id) {

        // finds the group or throws an exception if it does not exist
        CorporateGroup group = corporateGroupRepository.findById(id)
                .orElseThrow(() -> new CorporateGroupNotFoundException(
                        "Corporate group not found with ID: " + id
                ));

        // deletes the corporate group from the database
        corporateGroupRepository.delete(group);
    }
}
