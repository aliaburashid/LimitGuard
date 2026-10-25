package com.example.limitguard.controller;

import com.example.limitguard.documentation.CorporateGroupApi;
import com.example.limitguard.dto.CorporateGroupRequest;
import com.example.limitguard.dto.CorporateGroupResponse;
import com.example.limitguard.service.CorporateGroupService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import java.util.List;

@RestController
@RequestMapping("/api/corporate-groups")
public class CorporateGroupController implements CorporateGroupApi {

    // gives this controller access to the corporate group service
    @Autowired
    private CorporateGroupService corporateGroupService;

    //--------------------------------------------------------

    // creates a new corporate group
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER')")
    @Override
    public ResponseEntity<CorporateGroupResponse> createGroup(
            @Valid @RequestBody CorporateGroupRequest request) {

        // call the service to create and save the group
        CorporateGroupResponse createdGroup = corporateGroupService.createGroup(request);

        // return the created group with HTTP 201 Created
        return ResponseEntity.status(HttpStatus.CREATED).body(createdGroup);
    }

    // gets one corporate group by its ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER', 'RISK_OFFICER', 'ADMIN')")
    @Override
    public ResponseEntity<CorporateGroupResponse> getGroupById(@PathVariable Long id) {

        // sends the group ID to the service to find the corporate group
        CorporateGroupResponse group = corporateGroupService.getGroupById(id);

        // returns the corporate group with 200 OK
        return new ResponseEntity<>(group, HttpStatus.OK);
    }

    // gets all corporate groups with pagination and sorting
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER', 'RELATIONSHIP_MANAGER')")
    @Override
    public ResponseEntity<Page<CorporateGroupResponse>> getAllGroups(

            // sets the default page size to 10 and sorts groups by id
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC)
            Pageable pageable) {

        // gets the requested page of corporate groups from the service
        Page<CorporateGroupResponse> groups = corporateGroupService.getAllGroups(pageable);

        // returns the paginated groups with status 200
        return ResponseEntity.ok(groups);
    }

    // updates an existing corporate group
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER')")
    @Override
    public ResponseEntity<CorporateGroupResponse> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody CorporateGroupRequest request) {

        // sends the group ID and updated information to the service
        CorporateGroupResponse updatedGroup = corporateGroupService.updateGroup(id, request);

        // returns the updated corporate group with 200 OK
        return new ResponseEntity<>(updatedGroup, HttpStatus.OK);
    }

    // deletes an existing corporate group
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER')")
    @Override
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {

        // sends the group ID to the service to delete the corporate group
        corporateGroupService.deleteGroup(id);

        // returns 204 No Content after successful deletion
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
