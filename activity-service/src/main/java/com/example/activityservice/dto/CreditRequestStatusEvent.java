package com.example.activityservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Stores the credit request update sent to React through SSE
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreditRequestStatusEvent {

    private Long creditRequestId;
    private String status;
}
