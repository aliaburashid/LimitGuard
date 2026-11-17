package com.example.activityservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CounterpartyStatusNotification {

    // Fields used to deliver a counterparty status update to one institution.
    private Long id;
    private Long financialInstitutionId;
    private String eventType;
    private Long counterpartyId;
    private String status;
}
