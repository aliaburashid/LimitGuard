package com.example.limitguard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class CounterpartyRequest {
    // the counterparty name is required
    @NotBlank(message = "Counterparty name is required")
    private String name;
}
