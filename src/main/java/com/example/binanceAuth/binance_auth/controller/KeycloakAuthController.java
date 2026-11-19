package com.example.binanceAuth.binance_auth.controller;

import java.util.Map;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.binanceAuth.binance_auth.model.RegisterRequest;
import com.example.binanceAuth.binance_auth.service.RegistrationService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class KeycloakAuthController {

    private final RegistrationService registrationService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.register(request));
    }

    /**
     * Returns claims from the Keycloak access token after Spring Security
     * has verified its signature, issuer and expiration.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> currentUser(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(Map.of(
                "keycloakId", jwt.getSubject(),
                "username", jwt.getClaimAsString("preferred_username") == null
                        ? "" : jwt.getClaimAsString("preferred_username"),
                "email", jwt.getClaimAsString("email") == null
                        ? "" : jwt.getClaimAsString("email"),
                "emailVerified", Boolean.TRUE.equals(
                        jwt.getClaimAsBoolean("email_verified"))));
    }
}
