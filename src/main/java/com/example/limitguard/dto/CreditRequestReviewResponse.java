package com.example.limitguard.dto;

import com.example.limitguard.enums.CreditRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CreditRequestReviewResponse {

    // Risk Officer needs context like this:
    //    Request #2
    //    Requested amount: £600,000
    //    Requester: Alia ...
    //
    //    Counterparty: Bahrain Trading Company
    //
    //    Limit:      £1,000,000
    //    Used:       £500,000
    //    Reserved:   £450,000
    //    Headroom:    £50,000

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
}