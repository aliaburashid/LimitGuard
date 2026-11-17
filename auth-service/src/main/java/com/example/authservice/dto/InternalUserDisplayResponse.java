package com.example.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InternalUserDisplayResponse {

    private Long userId;
    private String fullName;
    private Long financialInstitutionId;
    private String financialInstitutionName;
}
