package com.example.limitguard.controller;

import com.example.limitguard.dto.LoginResponse;
import com.example.limitguard.security.SsoConfiguration;
import com.example.limitguard.security.SsoLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

// public work-account entry points; never returns provider secrets
@RestController
@RequestMapping("/api/auth/sso")
public class SsoController {
    private final SsoConfiguration providers;
    private final SsoLoginService login;

    public SsoController(SsoConfiguration providers, SsoLoginService login) {
        this.providers = providers;
        this.login = login;
    }

    @GetMapping("/providers")
    @SecurityRequirements
    @Operation(summary = "Check which work sign-in providers are configured")
    public Map<String, Boolean> providers() {
        return Map.of("google", providers.googleEnabled(), "microsoft", providers.microsoftEnabled());
    }

    @PostMapping(value = "/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Exchange a successful provider login once for a LimitGuard JWT")
    public ResponseEntity<LoginResponse> exchange(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        // JSON-only requests cannot be submitted by a cross-origin HTML form
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(login.exchange(request));
    }
}
