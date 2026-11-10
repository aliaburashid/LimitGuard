package com.example.limitguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerificationEmailChangeRequest {
    @NotBlank
    @Pattern(regexp = "[a-fA-F0-9-]{36}", message = "Invalid verification request")
    private String challengeId;

    @NotBlank
    @Email(message = "Enter a valid work email address")
    private String email;
}
