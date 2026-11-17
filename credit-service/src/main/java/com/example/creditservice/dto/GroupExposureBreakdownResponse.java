package com.example.creditservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// DTO containing a corporate group's credit exposure breakdown.
@Data // Generates getters, setters, equals, hashCode and toString.
@NoArgsConstructor // Creates an empty constructor for JSON deserialization.
@AllArgsConstructor // Creates a constructor accepting all fields.
public class GroupExposureBreakdownResponse {

    // Maximum credit amount allowed for the group.
    private BigDecimal limitAmount;

    // Credit amount currently being used.
    private BigDecimal usedAmount;

    // Credit amount reserved for pending requests.
    private BigDecimal reservedAmount;

    // Remaining credit capacity available to the group.
    private BigDecimal availableHeadroom;

    // Individual credit facilities contributing to the group's exposure.
    private List<CreditExposureResponse> facilities;
}
