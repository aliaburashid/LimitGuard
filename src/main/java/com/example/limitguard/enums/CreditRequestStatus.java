package com.example.limitguard.enums;

public enum CreditRequestStatus {

    // no APPROVED state, approval will move an eligible pending request into RESERVED
    PENDING_APPROVAL,
    RESERVED,
    USED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
