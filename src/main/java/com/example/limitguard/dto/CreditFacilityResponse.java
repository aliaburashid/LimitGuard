package com.example.limitguard.dto;

import com.example.limitguard.enums.CounterpartyStatus;

// current facility balances and the directory label used to display them
public record CreditFacilityResponse(
        CreditExposureResponse exposure,
        String name,
        CounterpartyStatus status
) {
}
