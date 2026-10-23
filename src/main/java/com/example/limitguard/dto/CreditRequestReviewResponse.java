package com.example.limitguard.dto;

import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.enums.CounterpartyStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CreditRequestReviewResponse {

    // credit request information
    private Long creditRequestId;
    private BigDecimal requestedAmount;
    private CreditRequestStatus status;
    private LocalDateTime createdAt;

    // requester information
    private Long requesterId;
    private String requesterName;

    // counterparty information
    private Long counterpartyId;
    private String counterpartyName;

    // credit limit and current exposure information
    private Long creditLimitId;
    private BigDecimal limitAmount;
    private BigDecimal usedAmount;
    private BigDecimal reservedAmount;
    private BigDecimal availableHeadroom;
    private LocalDateTime expiresAt;
    private CounterpartyStatus counterpartyStatus;
    private Long financialInstitutionId;
    private String financialInstitutionName;
}
