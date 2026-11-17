package com.example.creditservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CounterpartyStatusNotification {

    // Fields used to deliver a counterparty status update to one institution.
    private final Long id;
    private final Long financialInstitutionId;
    private final String eventType;
    private final Long counterpartyId;
    private final String status;
}
