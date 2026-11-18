package com.example.activityservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// The counterparty fields sent to the browser.
@Getter
@AllArgsConstructor
public class CounterpartyStatusEvent {

    private final Long counterpartyId;
    private final String status;
}
