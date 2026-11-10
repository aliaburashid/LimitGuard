package com.example.limitguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerificationCodeRequest {
    @NotBlank
    @Pattern(regexp = "[a-fA-F0-9-]{36}", message = "Invalid verification request")
    private String challengeId;

    // leading zeros are valid, so the code is a string rather than a number
    @NotBlank
    @Pattern(regexp = "[0-9]{6}", message = "Enter the six-digit code from your email")
    private String code;
}
