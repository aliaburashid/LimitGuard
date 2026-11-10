package com.example.limitguard.controller;

import com.example.limitguard.dto.*;
import com.example.limitguard.service.EmailVerificationCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

// handles the code entered after registration, rather than a verification link
@RestController
@RequestMapping("/api/auth/users/verification-code")
@SecurityRequirements
public class VerificationCodeController {
    private final EmailVerificationCodeService verification;

    public VerificationCodeController(EmailVerificationCodeService verification) {
        this.verification = verification;
    }

    @PostMapping("/request")
    @Operation(summary = "Resume verification for an unverified account after checking its password")
    public ResponseEntity<VerificationCodeResponse> request(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                verification.requestForPendingAccount(request.getEmail(), request.getPassword()));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify a pending registration using its six-digit email code")
    public ResponseEntity<Map<String, String>> verify(@Valid @RequestBody VerificationCodeRequest request) {
        if (!verification.verify(request.getChallengeId(), request.getCode())) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of(
                    "message", "The code is incorrect, expired or no longer available. Request a new code if needed."));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "message", "Email verified. You can now sign in."));
    }

    @PostMapping("/resend")
    @Operation(summary = "Send a new code after the 60-second resend cooldown")
    public ResponseEntity<VerificationCodeResponse> resend(@Valid @RequestBody VerificationResendRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(verification.resend(request.getChallengeId()));
    }

    @PostMapping("/change-email")
    @Operation(summary = "Change the email on this pending registration and send a new code")
    public ResponseEntity<VerificationCodeResponse> changeEmail(@Valid @RequestBody VerificationEmailChangeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(verification.changeEmail(request.getChallengeId(), request.getEmail()));
    }
}
