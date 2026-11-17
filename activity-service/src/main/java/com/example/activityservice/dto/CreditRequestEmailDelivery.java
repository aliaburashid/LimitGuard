package com.example.activityservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Holds the information needed to send one queued email.
@Getter
@AllArgsConstructor
public class CreditRequestEmailDelivery {

    private final long eventId;
    private final long financialInstitutionId;
    private final long creditRequestId;
    private final long requesterId;
    private final String eventType;
    private final String reason;
}
