package com.example.binanceAuth.binance_auth.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class KeycloakAdminService {

    @Value("${keycloak.base-url}")
    private String keycloakBaseUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin.client-id}")
    private String adminClientId;

    @Value("${keycloak.admin.client-secret}")
    private String adminClientSecret;

    @Value("${keycloak.registration.send-verification-email:false}")
    private boolean sendVerificationEmail;

    private final RestTemplate restTemplate = new RestTemplate();

    public String createUser(
            String username,
            String email,
            String password,
            String firstName,
            String lastName) {

        String adminToken = getAdminAccessToken();
        String usersUrl = adminBaseUrl() + "/users";

        HttpHeaders headers = jsonHeaders(adminToken);

        Map<String, Object> credential = new HashMap<>();
        credential.put("type", "password");
        credential.put("value", password);
        credential.put("temporary", false);

        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("email", email);
        user.put("firstName", firstName);
        user.put("lastName", lastName);
        user.put("enabled", true);
        user.put("emailVerified", false);
        user.put("credentials", List.of(credential));
        user.put("requiredActions", List.of("VERIFY_EMAIL"));

        ResponseEntity<Void> response = restTemplate.exchange(
                usersUrl,
                HttpMethod.POST,
                new HttpEntity<>(user, headers),
                Void.class);

        if (response.getStatusCode() != HttpStatus.CREATED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Keycloak did not create the user");
        }

        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        if (location == null || location.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Keycloak created the user but returned no user Location header");
        }

        String keycloakUserId = location.substring(location.lastIndexOf('/') + 1);

        if (sendVerificationEmail) {
            sendVerificationEmail(keycloakUserId, adminToken);
        }

        return keycloakUserId;
    }

    public void deleteUser(String keycloakUserId) {
        restTemplate.exchange(
                adminBaseUrl() + "/users/" + keycloakUserId,
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders(getAdminAccessToken())),
                Void.class);
    }

    private void sendVerificationEmail(String userId, String adminToken) {
        restTemplate.exchange(
                adminBaseUrl() + "/users/" + userId + "/send-verify-email",
                HttpMethod.PUT,
                new HttpEntity<>(jsonHeaders(adminToken)),
                Void.class);
    }

    private String getAdminAccessToken() {
        String tokenUrl = keycloakBaseUrl + "/realms/" + realm
                + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", adminClientId);
        form.add("client_secret", adminClientSecret);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        Map<String, Object> body = response.getBody();
        if (body == null || body.get("access_token") == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Keycloak admin token response was empty");
        }

        return Objects.toString(body.get("access_token"));
    }

    private HttpHeaders jsonHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken);
        return headers;
    }

    private String adminBaseUrl() {
        return keycloakBaseUrl + "/admin/realms/" + realm;
    }
}
