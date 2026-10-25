package com.example.limitguard.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CorporateGroupMembershipRequest {

    // Means: we're assigning counterparty 5 to corporate group 1.
    //{
    //  "corporateGroupId": 1,
    //  "counterpartyId": 5
    //}


    // the id of the corporate group we want to add the counterparty to
    @NotNull(message = "corporate group id is required")
    private Long corporateGroupId;

    // the id of the counterparty we want to assign to the group
    @NotNull(message = "counterparty id is required")
    private Long counterpartyId;
}