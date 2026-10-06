package com.example.limitguard.dto;

import com.example.limitguard.enums.CreditRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@Setter
public class CreditRequestResponse {

    private Long id;
    private BigDecimal amount;
    private CreditRequestStatus status;
    private Long creditLimitId;
    private Long counterpartyId;
    private Long requesterId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}