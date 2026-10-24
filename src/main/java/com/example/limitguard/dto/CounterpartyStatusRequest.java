package com.example.limitguard.dto;

import com.example.limitguard.enums.CounterpartyStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class CounterpartyStatusRequest {

    // status must be provided when changing a counterparty's status
    @Schema(
            description = "New status to assign to the counterparty. Must be a valid CounterpartyStatus enum value",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Counterparty status is required")
    private CounterpartyStatus status;
}
