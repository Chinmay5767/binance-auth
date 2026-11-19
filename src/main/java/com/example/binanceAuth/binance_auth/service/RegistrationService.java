package com.example.binanceAuth.binance_auth.service;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import com.example.binanceAuth.binance_auth.model.RegisterRequest;
import com.example.binanceAuth.binance_auth.repository.AuthUserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final KeycloakAdminService keycloakAdminService;
    private final AuthUserRepo authUserRepo;

    @Transactional
    public Map<String, Object> register(RegisterRequest request) {

        if (authUserRepo.existsByEmailId(request.getEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email is already registered");
        }

        if (authUserRepo.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Username is already taken");
        }

        String keycloakUserId = keycloakAdminService.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getPassword(),
                request.getFirstName(),
                request.getLastName());

        try {
            AuthUserEntity user = new AuthUserEntity();
            user.setKeycloakId(keycloakUserId);
            user.setUsername(request.getUsername());
            user.setEmailId(request.getEmail());
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setEnabled(true);
            user.setEmailVerified(false);
            user.setMfaEnabled(false);
            user.setRoles(List.of("ROLE_USER"));

            // Passwords are owned by Keycloak. Do not store a duplicate locally.
            user.setPassword(null);

            AuthUserEntity savedUser = authUserRepo.saveAndFlush(user);

            return Map.of(
                    "message", "Registration successful",
                    "userId", savedUser.getUserId(),
                    "keycloakId", keycloakUserId,
                    "username", savedUser.getUsername(),
                    "email", savedUser.getEmailId(),
                    "emailVerificationRequired", true);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request may have created the same email/username.
            deleteKeycloakUserAfterLocalFailure(keycloakUserId);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email or username is already registered",
                    ex);
        } catch (RuntimeException ex) {
            deleteKeycloakUserAfterLocalFailure(keycloakUserId);
            throw ex;
        }
    }

    private void deleteKeycloakUserAfterLocalFailure(String keycloakUserId) {
        try {
            keycloakAdminService.deleteUser(keycloakUserId);
        } catch (Exception cleanupException) {
            log.error(
                    "Could not compensate Keycloak user creation for user id {}",
                    keycloakUserId,
                    cleanupException);
        }
    }
}
