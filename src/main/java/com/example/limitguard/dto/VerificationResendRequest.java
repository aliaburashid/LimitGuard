package com.example.limitguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerificationResendRequest {
    @NotBlank
    @Pattern(regexp = "[a-fA-F0-9-]{36}", message = "Invalid verification request")
    private String challengeId;
}
