package com.example.authservice.controller;

import com.example.authservice.dto.*;
import com.example.authservice.documentation.VerificationCodeApi;
import com.example.authservice.service.EmailVerificationCodeService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Handles the code entered after registration, rather than a verification link
@RestController
@RequestMapping("/api/auth/users/verification-code")
@SecurityRequirements
public class VerificationCodeController implements VerificationCodeApi {

    private final EmailVerificationCodeService verification;

    public VerificationCodeController(EmailVerificationCodeService verification) {
        this.verification = verification;
    }

    @Override
    @PostMapping("/request")
    public ResponseEntity<VerificationCodeResponse> request(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(verification.requestForPendingAccount(
                        request.getEmail(),
                        request.getPassword()
                ));
    }

    @Override
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(
            @Valid @RequestBody VerificationCodeRequest request) {

        if (!verification.verify(
                request.getChallengeId(),
                request.getCode())) {

            return ResponseEntity.badRequest()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of(
                            "message",
                            "The code is incorrect, expired or no longer available. Request a new code if needed."
                    ));
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "message",
                        "Email verified. You can now sign in."
                ));
    }

    @Override
    @PostMapping("/resend")
    public ResponseEntity<VerificationCodeResponse> resend(
            @Valid @RequestBody VerificationResendRequest request) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(verification.resend(request.getChallengeId()));
    }

    @Override
    @PostMapping("/change-email")
    public ResponseEntity<VerificationCodeResponse> changeEmail(
            @Valid @RequestBody VerificationEmailChangeRequest request) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(verification.changeEmail(
                        request.getChallengeId(),
                        request.getEmail()
                ));
    }
}
