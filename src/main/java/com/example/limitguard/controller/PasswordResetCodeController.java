package com.example.limitguard.controller;

import com.example.limitguard.dto.ForgotPasswordRequest;
import com.example.limitguard.dto.VerificationCodeRequest;
import com.example.limitguard.dto.VerificationCodeResponse;
import com.example.limitguard.dto.VerificationResendRequest;
import com.example.limitguard.service.PasswordResetCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/users/password-reset-code")
@SecurityRequirements
public class PasswordResetCodeController {

    private final PasswordResetCodeService passwordResetCodeService;

    public PasswordResetCodeController(PasswordResetCodeService passwordResetCodeService) {
        this.passwordResetCodeService = passwordResetCodeService;
    }

    @PostMapping("/request")
    @Operation(summary = "Email a six-digit password reset code")
    public ResponseEntity<VerificationCodeResponse> requestCode(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                passwordResetCodeService.requestCode(request.getEmail()));
    }

    @PostMapping("/resend")
    @Operation(summary = "Resend a password reset code after the cooldown")
    public ResponseEntity<VerificationCodeResponse> resendCode(@Valid @RequestBody VerificationResendRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                passwordResetCodeService.resendCode(request.getChallengeId()));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify the code before opening the password reset form")
    public ResponseEntity<Map<String, String>> verifyCode(@Valid @RequestBody VerificationCodeRequest request) {
        var resetToken = passwordResetCodeService.verifyCode(request.getChallengeId(), request.getCode());
        if (resetToken.isEmpty()) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of(
                    "message", "The code is incorrect, expired or no longer available. Request a new code if needed."));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token", resetToken.get()));
    }
}
