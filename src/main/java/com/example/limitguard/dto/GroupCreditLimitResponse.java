package com.example.limitguard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GroupCreditLimitResponse {

    // unique credit limit id
    private Long id;

    // corporate group information
    private Long corporateGroupId;
    private String corporateGroupName;

    // identifies the bank that owns this credit limit
    private Long financialInstitutionId;

    // approved credit limit amount
    private BigDecimal amount;

    // timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
