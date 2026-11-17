package com.example.creditservice.dto;

import com.example.creditservice.enums.CreditRequestStatus;
import com.example.creditservice.enums.CounterpartyStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

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
    private String counterpartyName;
    private CounterpartyStatus counterpartyStatus;
    private String requesterName;
    private Long financialInstitutionId;
    private String financialInstitutionName;
}
