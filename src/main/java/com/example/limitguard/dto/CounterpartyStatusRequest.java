package com.example.limitguard.dto;

import com.example.limitguard.enums.CounterpartyStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CounterpartyStatusRequest {

    // status must be provided when changing a counterparty's status
    @NotNull(message = "Counterparty status is required")
    private CounterpartyStatus status;
}