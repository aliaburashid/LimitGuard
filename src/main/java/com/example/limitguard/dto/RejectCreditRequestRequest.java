package com.example.limitguard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectCreditRequestRequest {

    @NotBlank(message = "Rejection reason is required")
    private String reason;
}