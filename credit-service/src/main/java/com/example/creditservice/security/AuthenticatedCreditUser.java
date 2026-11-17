package com.example.creditservice.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Represents a successfully authenticated user inside Credit Service.
// The user's information comes from Auth Service's authorization endpoint,
// not directly from the users table.
@Getter
@AllArgsConstructor
public class AuthenticatedCreditUser {

    // Unique ID of the authenticated user
    private final Long userId;

    // Email address extracted from the validated JWT
    private final String emailAddress;

    // Current user role retrieved from Auth Service
    private final String userRole;

    // ID of the financial institution the user belongs to
    private final Long financialInstitutionId;
}
