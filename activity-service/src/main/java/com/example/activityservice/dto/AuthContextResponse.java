package com.example.activityservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Stores the user information returned by Auth Service
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthContextResponse {

    private Long userId;
    private String role;
    private String status;
    private Long financialInstitutionId;
}
