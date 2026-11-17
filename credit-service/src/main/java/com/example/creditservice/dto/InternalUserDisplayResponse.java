package com.example.creditservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InternalUserDisplayResponse {

    private Long userId;
    private String fullName;
    private Long financialInstitutionId;
    private String financialInstitutionName;
}
