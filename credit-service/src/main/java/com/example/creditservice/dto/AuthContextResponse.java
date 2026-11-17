package com.example.creditservice.dto;

import lombok.*;

// receive user information from Auth Service
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthContextResponse {

    // Unique ID of the authenticated user
    private Long userId;

    // Current role of the user, such as RELATIONSHIP_MANAGER
    private String role;

    // Current account status, such as ACTIVE
    private String status;

    // ID of the financial institution the user belongs to
    private Long financialInstitutionId;
}
