package com.example.limitguard.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CorporateGroupMembershipResponse {

    // the unique id of the membership
    private Long id;
    // the id of the corporate group
    private Long corporateGroupId;
    // the name of the corporate group
    private String corporateGroupName;
    // the id of the counterparty assigned to the group
    private Long counterpartyId;
}