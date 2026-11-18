package com.example.limitguard.dto;

import com.example.limitguard.enums.CounterpartyStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO containing a credit facility's exposure and directory information.
@Data // Generates getters, setters, equals, hashCode and toString.
@NoArgsConstructor // Creates an empty constructor for JSON deserialization.
@AllArgsConstructor // Creates a constructor accepting all fields.
public class CreditFacilityResponse {

    // Current credit facility balances and exposure information.
    private CreditExposureResponse exposure;

    // Name of the credit facility or counterparty.
    private String name;

    // Current status of the counterparty.
    private CounterpartyStatus status;
}
