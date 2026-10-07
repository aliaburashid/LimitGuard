package com.example.limitguard.dto;

import com.example.limitguard.enums.CreditRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

// Data sent to clients when a credit request changes status
@Getter
@AllArgsConstructor
public class CreditRequestStatusEvent {

    // Which request changed?
    private Long creditRequestId;
    // What is its new status?
    private CreditRequestStatus status;
}