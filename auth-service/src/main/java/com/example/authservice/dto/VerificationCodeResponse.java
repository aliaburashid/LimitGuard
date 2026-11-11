package com.example.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.Instant;

// the browser receives timing information, never the verification code or its hash
@Getter
@AllArgsConstructor
public class VerificationCodeResponse {
    private String challengeId;
    private String email;
    private Instant expiresAt;
    private Instant resendAfter;
}
