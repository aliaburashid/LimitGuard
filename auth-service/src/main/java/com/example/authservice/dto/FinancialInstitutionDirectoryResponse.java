package com.example.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// returns only the information needed to choose an institution during registration
@AllArgsConstructor
@Getter
public class FinancialInstitutionDirectoryResponse {

    // the ID sent with the existing registration request
    private Long id;

    // the institution name displayed in the registration dropdown
    private String name;
}
