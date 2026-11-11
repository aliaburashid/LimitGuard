package com.example.authservice.controller;

import com.example.authservice.dto.ForgotPasswordRequest;
import com.example.authservice.dto.VerificationCodeRequest;
import com.example.authservice.dto.VerificationCodeResponse;
import com.example.authservice.dto.VerificationResendRequest;
import com.example.authservice.service.PasswordResetCodeService;
import com.example.authservice.documentation.PasswordResetCodeApi;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/users/password-reset-code")
@SecurityRequirements
public class PasswordResetCodeController implements PasswordResetCodeApi {

    private final PasswordResetCodeService passwordResetCodeService;

    public PasswordResetCodeController(PasswordResetCodeService passwordResetCodeService) {
        this.passwordResetCodeService = passwordResetCodeService;
    }

    @PostMapping("/request")
    @Override
    public ResponseEntity<VerificationCodeResponse> requestCode(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                passwordResetCodeService.requestCode(request.getEmail()));
    }

    @PostMapping("/resend")
    @Override
    public ResponseEntity<VerificationCodeResponse> resendCode(@Valid @RequestBody VerificationResendRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                passwordResetCodeService.resendCode(request.getChallengeId()));
    }

    @PostMapping("/verify")
    @Override
    public ResponseEntity<Map<String, String>> verifyCode(@Valid @RequestBody VerificationCodeRequest request) {
        var resetToken = passwordResetCodeService.verifyCode(request.getChallengeId(), request.getCode());
        if (resetToken.isEmpty()) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of(
                    "message", "The code is incorrect, expired or no longer available. Request a new code if needed."));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token", resetToken.get()));
    }
}
