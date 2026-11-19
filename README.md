# binance-auth

Authentication microservice for the Binance / crypto analytics project.

## Keycloak Authorization Code and registration feature

This feature branch adds:
- `POST /auth/register`: creates a user in Keycloak using the Admin REST API and persists a local profile.
- `GET /auth/me`: returns selected claims from a verified Keycloak access token.
- Spring Security OAuth2 Login using the Authorization Code grant.
- Resource-server validation of Keycloak access tokens, including mapping Keycloak realm roles to Spring authorities.

### 1. Keycloak clients

Create two clients in the same realm configured for this service.

**User login client** (example: `crypto-user-client`)
- Client authentication: ON (confidential client for the server-side Spring OAuth2 login).
- Standard flow: ON.
- Direct access grants: OFF.
- Valid redirect URI for Spring: `http://localhost:8081/login/oauth2/code/keycloak`.
- For Postman Authorization Code testing, add Postman's callback URL, commonly `https://oauth.pstmn.io/v1/callback`.
- Do not enable PKCE for this Postman confidential-client setup because this implementation follows the requested no-PKCE configuration. A future browser SPA must use its own public client with Authorization Code + PKCE; never put a confidential client secret in React.

**Service client** (example: `crypto-service-client`)
- Client authentication: ON.
- Service accounts: ON.
- Direct access grants: OFF.
- Assign its service account only the required `realm-management` roles: `manage-users`, `view-users`, and `query-users` (reduce further if your Keycloak permissions allow it).

Use a dedicated application realm rather than the `master` realm for production. Both clients, the Admin API service account and the Spring issuer configuration must refer to the same realm.

### 2. Configuration

Copy the relevant properties from `src/main/resources/application-keycloak-example.properties` into the active Spring Boot configuration. Supply secrets as environment variables:

- `KEYCLOAK_URL`: Keycloak base URL reachable by the Spring application, e.g. `http://localhost:8080` when Spring runs on the host.
- `KEYCLOAK_REALM`: realm name.
- `KEYCLOAK_USER_CLIENT_ID` / `KEYCLOAK_USER_CLIENT_SECRET`: confidential user-login client credentials.
- `KEYCLOAK_ADMIN_CLIENT_ID` / `KEYCLOAK_ADMIN_CLIENT_SECRET`: service-account client credentials.

If Spring runs inside Docker Compose, use the Keycloak service DNS name and internal port for backend-to-Keycloak calls. The issuer in the JWT's `iss` claim must exactly match `spring.security.oauth2.resourceserver.jwt.issuer-uri`; configure Keycloak hostname/proxy settings consistently if host and container URLs differ.

Do not commit real secrets. Configure Keycloak SMTP before expecting verification emails. The user is created with the `VERIFY_EMAIL` required action; the optional immediate send-verify-email call is disabled by default.

### 3. Registration request

```http
POST http://localhost:8081/auth/register
Content-Type: application/json

{
  "username": "chinmay",
  "email": "chinmay@example.com",
  "password": "ChangeThisPassword123!",
  "firstName": "Chinmay",
  "lastName": "Beedkar"
}
```

Expected status: `201 Created`. Duplicate username/email returns `409 Conflict`. Passwords are stored in Keycloak only, not in the local `auth_user` table. If local persistence fails after Keycloak creates the user, the service attempts a compensating Keycloak delete.

### 4. Test Authorization Code with Postman

In Postman, select OAuth 2.0 and configure:
- Grant Type: Authorization Code
- Callback URL: `https://oauth.pstmn.io/v1/callback`
- Auth URL: `{KEYCLOAK_URL}/realms/{KEYCLOAK_REALM}/protocol/openid-connect/auth`
- Access Token URL: `{KEYCLOAK_URL}/realms/{KEYCLOAK_REALM}/protocol/openid-connect/token`
- Client ID / Client Secret: user-login client
- Scope: `openid profile email`
- Client Authentication: Send as Basic Auth header
- PKCE: disabled for this confidential Postman client

Click **Get New Access Token**, authenticate in the Keycloak browser window, then use the returned **access token** as the Bearer token.

Call:

```http
GET http://localhost:8081/auth/me
Authorization: Bearer <access_token>
```

Do not send the ID token to protected APIs; use the access token.

### 5. Spring browser login

The existing OAuth2 login entry point remains available at:

`http://localhost:8081/oauth2/authorization/keycloak`

Spring handles the authorization-code callback at `/login/oauth2/code/keycloak` and redirects to the existing `/auth/success` handler.

### Important compatibility note

The repository contains older custom JWT, refresh-token, MFA, session and logout flows. This branch deliberately leaves those legacy flows in place rather than silently replacing them. They are not interchangeable with Keycloak-issued access tokens: endpoints that call the legacy `TokenService` still expect the application's custom token format. Use the new `/auth/me` endpoint to validate the Keycloak access-token path. A follow-up migration should unify logout, refresh, MFA and session management around Keycloak before retiring the custom token implementation.

### Build and verification status

The repository snapshot currently does not contain a Maven `pom.xml` or Gradle build file, and it does not include an active `application.properties`. Therefore this branch cannot be built or integration-tested from the checked-in repository as it stands. Add/restore the project build file and active configuration, then run the application's normal tests and a Keycloak-backed registration/login test before merging.
