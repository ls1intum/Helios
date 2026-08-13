# GitHub User-Token Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Helios always hold a valid GitHub *user* access token for deployment approvals, by owning the refresh loop itself — so auto- and in-app approval keep working regardless of how long ago the reviewer logged in.

**Architecture:** Helios stops depending on Keycloak's (non-refreshing) token-exchange for the live GitHub token. Instead it persists each user's GitHub **refresh token** (seeded once from Keycloak's broker *retrieve-token* endpoint), then refreshes directly against GitHub's OAuth token endpoint on demand, caching the ~8 h access token and rotating the refresh token on every use. Refresh tokens are 6-month credentials, so they are encrypted at rest.

**Tech Stack:** Java 21+, Spring Boot, Spring Data JPA, Flyway, OkHttp, Jackson, JUnit 5 + Mockito, zonky embedded Postgres (integration tests), Keycloak 26.1.3, GitHub App OAuth.

## Global Constraints

- Java source compiled on JDK 25; language level per existing `build.gradle` — match surrounding code.
- Checkstyle (`server/checkstyle.xml`, Google-based): **max line length 100**, 2-space indent, alphabetical imports (static first), Javadoc on public types.
- Dependencies pinned exactly — **never** use `^`/version ranges; reuse libraries already on the classpath (OkHttp, Jackson) — add none.
- Flyway migrations are immutable and sequential; next free version is **V59**. Never edit an applied migration.
- Deployment-referenced integrity: do not weaken existing approval-path behaviour (audit rows, `FAILED_AT_GITHUB`, self-review checks).
- All new secrets are provided via environment variables (compose), never committed.
- Every task ends green: `server/gradlew -p server :application-server:test` (targeted with `--tests`).

---

## Prerequisites (ops / prod config — NOT code; do before Task 2)

These are operator steps. Document exact values in the PR description; do not hard-code secrets.

**P1. GitHub App OAuth client secret.** The refresh grant needs the login GitHub App's `client_id` + `client_secret`. Helios already has `GITHUB_CLIENT_ID`; provision the matching secret as `GITHUB_CLIENT_SECRET`. Confirm the App has **"Expire user authorization tokens" enabled** (Developer settings → the App → Optional features) — that is what makes GitHub issue refresh tokens at all.

**P2. Token-encryption key.** Provision `HELIOS_TOKEN_ENCRYPTION_KEY` = base64-encoded 256-bit key (`openssl rand -base64 32`). App fails fast at startup if unset (see Task 3) so refresh tokens are never written in plaintext.

**P3. Keycloak retrieve-token permission — CONFIRMED by the Task-1 spike (2026-07-19).** In realm `helios`, the `github` IdP already has `Store tokens = ON`, and a headless impersonation token-exchange for a user already succeeds (spike Step A → HTTP 200). The only missing grant: **Identity Providers → github → Permissions → enable, then the `token` (retrieve-token) permission → add a client policy for `helios-token-exchange`.** Without it the retrieve endpoint returns exactly:

```
StepB(retrieve-token /broker/github/token) http=403
{"errorMessage":"Client [helios-token-exchange] not authorized to retrieve tokens from identity provider [github]."}
```

This is a mirror of the impersonation permission that already lets Step A work; low-risk and reversible. After granting it, re-run the spike (`scratchpad/kc_spike.sh`) — Step B must return 200 with a body containing `refresh_token` — before implementing Task 5.

---

## Risk & the Task-1 spike

The entire "seed via retrieve-token" approach hinges on Helios being able to obtain a user's stored GitHub **refresh token** *headlessly* (no browser session). Research confirms the retrieve-token endpoint returns the full stored token JSON (incl. `refresh_token`) for the `github` provider, and that a bearer carrying `broker`/`read-token` can call it — but the headless/impersonation specifics must be proven on our Keycloak before building on them. **Task 1 is a throwaway spike that proves this end-to-end.** If it fails, stop and escalate (fallback = Keycloak event-listener SPI, a different plan).

---

## File Structure

- `server/application-server/src/main/resources/db/migration/V59__create_github_user_token.sql` — token store table.
- `.../auth/github/token/GitHubUserToken.java` — JPA entity (one row per GitHub login).
- `.../auth/github/token/GitHubUserTokenRepository.java` — Spring Data repo.
- `.../auth/github/token/TokenCipher.java` — AES-GCM encrypt/decrypt of token strings.
- `.../auth/github/token/GitHubOAuthTokenClient.java` — GitHub refresh-grant HTTP call.
- `.../auth/github/token/KeycloakBrokerTokenClient.java` — seed refresh token via Keycloak retrieve-token.
- `.../auth/github/token/GitHubUserTokenService.java` — orchestrates cache → refresh → seed; the public API.
- `.../auth/github/token/GitHubReauthRequiredException.java` — extends `IOException`; signals "user must re-login".
- `.../auth/github/token/GitHubUserTokenRecord.java` — immutable value carrying tokens + expiries.
- Modify `.../github/GitHubService.java` — use `GitHubUserTokenService` instead of `GitHubAuthBroker.exchangeToken`.
- Modify `.../deployment/approval/DeploymentReviewActionService.java` — treat `GitHubReauthRequiredException` like the 401 case (actionable reason).
- Modify `.../github/GitHubConfig.java` (+ `application.yml`, `compose.prod.yaml`) — new config keys.
- Tests alongside each unit; one integration test for the repo/migration + cipher round-trip.

---

### Task 1: Spike — prove headless refresh-token retrieval (throwaway)

**Files:** none committed (a scratch script run against staging Keycloak).

**Interfaces:** Produces a documented yes/no on P3 + the exact request shapes Task 5 will encode.

- [ ] **Step 1:** Mint an *internal* Keycloak access token for a known GitHub user via impersonation token-exchange (no `requested_issuer`), using `HELIOS_TOKEN_EXCHANGE_CLIENT`/`_SECRET`:
```bash
curl -s -XPOST "$ISSUER/protocol/openid-connect/token" \
  -d client_id=$TEC -d client_secret=$TES \
  -d grant_type=urn:ietf:params:oauth:grant-type:token-exchange \
  -d requested_subject=<github-login> \
  -d requested_token_type=urn:ietf:params:oauth:token-type:access_token
```
- [ ] **Step 2:** Call retrieve-token with that bearer and confirm `refresh_token` is present:
```bash
curl -s "$ISSUER/broker/github/token" -H "Authorization: Bearer <internal_token>"
```
Expected: JSON containing `access_token`, `refresh_token`, `refresh_token_expires_in`.
- [ ] **Step 3:** If `refresh_token` is absent or the call 403s, STOP — fix P3 (add `read-token`) or escalate to the SPI fallback. Record the working request/response shapes in the PR.

---

### Task 2: Config plumbing for GitHub OAuth + encryption key

**Files:**
- Modify: `server/application-server/src/main/java/de/tum/cit/aet/helios/github/GitHubConfig.java`
- Modify: `server/application-server/src/main/resources/application.yml`
- Modify: `compose.prod.yaml`

**Interfaces:**
- Produces: `GitHubConfig.getOauthClientId()`, `GitHubConfig.getOauthClientSecret()` (String getters via Lombok `@Getter`).

- [ ] **Step 1:** Add fields to `GitHubConfig` (Lombok `@Getter` already on the class-level fields it exposes; add `@Getter` per field to match existing style):
```java
  @Getter
  @Value("${github.oauthClientId:${github.clientId}}")
  private String oauthClientId;

  @Getter
  @Value("${github.oauthClientSecret:#{null}}")
  private String oauthClientSecret;
```
- [ ] **Step 2:** In `application.yml`, under the existing `github:` mapping, add:
```yaml
    oauthClientId: ${GITHUB_CLIENT_ID:}
    oauthClientSecret: ${GITHUB_CLIENT_SECRET:}
    tokenEncryptionKey: ${HELIOS_TOKEN_ENCRYPTION_KEY:}
```
- [ ] **Step 3:** In `compose.prod.yaml` (application-server `environment:`), add `GITHUB_CLIENT_SECRET=${GITHUB_CLIENT_SECRET}` and `HELIOS_TOKEN_ENCRYPTION_KEY=${HELIOS_TOKEN_ENCRYPTION_KEY}` near the existing GitHub vars.
- [ ] **Step 4:** Build to verify wiring: `server/gradlew -p server :application-server:compileJava` → BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit: `chore(auth): add GitHub OAuth client-secret and token-encryption config`.

---

### Task 3: `TokenCipher` (AES-GCM at rest)

**Files:**
- Create: `.../auth/github/token/TokenCipher.java`
- Test: `.../auth/github/token/TokenCipherTest.java`

**Interfaces:**
- Produces: `String TokenCipher.encrypt(String plaintext)`, `String TokenCipher.decrypt(String stored)` (Base64 `iv:ciphertext`). Constructed from `@Value("${github.tokenEncryptionKey}")`; throws `IllegalStateException` at construction if the key is blank.

- [ ] **Step 1:** Write the failing test:
```java
@Test
void encryptThenDecryptRoundTrips() {
  TokenCipher c = new TokenCipher("Base64Key32BytesElided=..."); // 32-byte base64
  String enc = c.encrypt("ghr_secret");
  assertNotEquals("ghr_secret", enc);
  assertEquals("ghr_secret", c.decrypt(enc));
}

@Test
void blankKeyFailsFast() {
  assertThrows(IllegalStateException.class, () -> new TokenCipher(" "));
}
```
- [ ] **Step 2:** Run → FAIL (class missing). `... --tests "*TokenCipherTest"`.
- [ ] **Step 3:** Implement AES/GCM/NoPadding (12-byte random IV per encrypt, 128-bit tag; store `base64(iv) + ":" + base64(ct)`; key = `Base64.getDecoder().decode(keyProp)` → `SecretKeySpec(…, "AES")`; blank key → `IllegalStateException`). Use `javax.crypto.*` + `java.security.SecureRandom` only.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit: `feat(auth): add TokenCipher for encrypting stored GitHub tokens`.

---

### Task 4: Token store — migration, entity, repository, integration test

**Files:**
- Create: `db/migration/V59__create_github_user_token.sql`
- Create: `.../auth/github/token/GitHubUserToken.java`, `GitHubUserTokenRepository.java`
- Create: `.../auth/github/token/GitHubUserTokenRecord.java`
- Test: `.../auth/github/token/GitHubUserTokenRepositoryIT.java` (zonky, mirror `WorkflowRunRetentionIntegrationTest` setup)

**Interfaces:**
- Produces: entity `GitHubUserToken` (fields `id`, `githubLogin`, `accessTokenEnc`, `refreshTokenEnc`, `accessTokenExpiresAt`, `refreshTokenExpiresAt`, `updatedAt`); `Optional<GitHubUserToken> GitHubUserTokenRepository.findByGithubLogin(String)`; record `GitHubUserTokenRecord(String accessToken, OffsetDateTime accessExpiresAt, String refreshToken, OffsetDateTime refreshExpiresAt)`.

- [ ] **Step 1:** Write migration:
```sql
CREATE TABLE public.github_user_token (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    github_login varchar(255) NOT NULL UNIQUE,
    access_token_enc text,
    refresh_token_enc text,
    access_token_expires_at timestamp(6) with time zone,
    refresh_token_expires_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL
);
```
- [ ] **Step 2:** Write entity + repository (JPA, matching repo conventions; `@Column(name = "...")` for snake_case).
- [ ] **Step 3:** Write the failing IT: insert via `save`, `findByGithubLogin` returns it; unique constraint on `github_login` enforced.
- [ ] **Step 4:** Run → PASS. `... --tests "*GitHubUserTokenRepositoryIT"`.
- [ ] **Step 5:** Commit: `feat(auth): persist per-user GitHub tokens (V59)`.

---

### Task 5: `KeycloakBrokerTokenClient` (seed refresh token)

**Files:**
- Create: `.../auth/github/token/KeycloakBrokerTokenClient.java`
- Test: `.../auth/github/token/KeycloakBrokerTokenClientTest.java` (OkHttp mocked, mirror `GitHubServiceTest` style)

**Interfaces:**
- Consumes: `issuerUri`, token-exchange client id/secret (same `@Value`s as `GitHubAuthBroker`), `OkHttpClient`, `ObjectMapper`.
- Produces: `GitHubUserTokenRecord KeycloakBrokerTokenClient.fetchStoredTokens(String githubLogin) throws IOException` — mints an internal impersonation token then GETs `/broker/github/token`, mapping `access_token`/`expires_in`/`refresh_token`/`refresh_token_expires_in` (relative to `now`) into the record. Throws `GitHubReauthRequiredException` if no refresh token is returned (user never linked / must re-login).

- [ ] **Step 1:** Write failing tests: (a) happy path returns record with refresh token; (b) missing `refresh_token` in body → `GitHubReauthRequiredException`; (c) non-2xx retrieve → `IOException`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement using the exact request shapes confirmed in Task 1.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit: `feat(auth): seed GitHub refresh tokens from Keycloak retrieve-token`.

---

### Task 6: `GitHubOAuthTokenClient` (refresh grant)

**Files:**
- Create: `.../auth/github/token/GitHubOAuthTokenClient.java`, `GitHubReauthRequiredException.java`
- Test: `.../auth/github/token/GitHubOAuthTokenClientTest.java`

**Interfaces:**
- Consumes: `GitHubConfig` (oauth client id/secret), `OkHttpClient`, `ObjectMapper`.
- Produces: `GitHubUserTokenRecord GitHubOAuthTokenClient.refresh(String refreshToken) throws IOException`. On GitHub error bodies (`bad_refresh_token`, `bad_verification_code`, `unauthorized`) throws `GitHubReauthRequiredException`. `GitHubReauthRequiredException extends IOException`.

- [ ] **Step 1:** Write failing tests: (a) `POST https://github.com/login/oauth/access_token` with `grant_type=refresh_token` → parses new `access_token`/`expires_in`/`refresh_token`/`refresh_token_expires_in`; (b) `{"error":"bad_refresh_token"}` → `GitHubReauthRequiredException`; assert request carries `client_id`/`client_secret`/`refresh_token` form fields and `Accept: application/json`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement (FormBody; expiries = `now + expires_in`/`refresh_token_expires_in`).
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit: `feat(auth): add GitHub OAuth refresh-token client`.

---

### Task 7: `GitHubUserTokenService` (cache → refresh → seed orchestration)

**Files:**
- Create: `.../auth/github/token/GitHubUserTokenService.java`
- Test: `.../auth/github/token/GitHubUserTokenServiceTest.java`

**Interfaces:**
- Consumes: `GitHubUserTokenRepository`, `TokenCipher`, `KeycloakBrokerTokenClient`, `GitHubOAuthTokenClient`, a `Clock` (inject for testability).
- Produces: `String GitHubUserTokenService.getValidAccessToken(String githubLogin) throws IOException`.

Logic (encode exactly):
1. `row = repo.findByGithubLogin(login)`.
2. If `row` present and `accessTokenExpiresAt > now + 60s` → return `cipher.decrypt(accessTokenEnc)`.
3. Else if `row` present and `refreshTokenEnc != null` and `refreshTokenExpiresAt > now` → `rec = oauth.refresh(cipher.decrypt(refreshTokenEnc))`; persist (encrypt both, rotate); return `rec.accessToken()`.
4. Else → `rec = broker.fetchStoredTokens(login)`; if `rec.accessToken()` still valid return it after persisting; otherwise immediately `rec = oauth.refresh(rec.refreshToken())`; persist; return.
5. `GitHubReauthRequiredException` propagates. Persistence always encrypts via `TokenCipher` and writes `updatedAt = now`. Use `@Transactional` per public call; refresh HTTP happens inside but is idempotent enough (rotation persisted immediately after).

- [ ] **Step 1:** Write failing tests covering each branch: fresh-cache hit (no network); expired-access→refresh (rotates + persists new refresh); no-row→seed→return; refresh throws `GitHubReauthRequiredException` propagates; expired refresh → re-seed.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement per logic above.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit: `feat(auth): GitHubUserTokenService with refresh + rotation`.

---

### Task 8: Rewire `GitHubService` to the token service

**Files:**
- Modify: `.../github/GitHubService.java` (`reviewPendingDeployment`, ~line 547)
- Modify: `.../github/GitHubServiceTest.java`

**Interfaces:**
- Consumes: `GitHubUserTokenService.getValidAccessToken`.
- Produces: unchanged public `approveDeploymentOnBehalfOfUser` / `rejectDeploymentOnBehalfOfUser` signatures.

- [ ] **Step 1:** Update `GitHubServiceTest`: replace `gitHubAuthBroker.exchangeToken(...)` stubbing with `gitHubUserTokenService.getValidAccessToken(login)` returning `"user-token"`; the null/failure case now stubs `getValidAccessToken` to throw `GitHubReauthRequiredException` and asserts it surfaces (the endpoint still 401s → `GitHubReviewException`). Keep the HTTP-500 → `GitHubReviewException` case.
- [ ] **Step 2:** Run → FAIL (constructor/field mismatch).
- [ ] **Step 3:** Inject `GitHubUserTokenService`; replace the `gitHubAuthBroker.exchangeToken(...)` block in `reviewPendingDeployment` with `String userGithubToken = gitHubUserTokenService.getValidAccessToken(githubUserLogin);`. Remove the now-unused `GitHubAuthBroker` dependency **only if** it has no other callers (it does not — verified). Keep the `Bearer` header logic.
- [ ] **Step 4:** Run → PASS: `... --tests "*GitHubServiceTest"`.
- [ ] **Step 5:** Commit: `refactor(github): approve deployments with the self-refreshing token service`.

---

### Task 9: Actionable reason for re-auth in the approval path

**Files:**
- Modify: `.../deployment/approval/DeploymentReviewActionService.java` (`gitHubFailureReason`, from PR #1198)
- Modify: `.../deployment/approval/DeploymentReviewActionServiceTest.java`

- [ ] **Step 1:** Add a failing test: `getValidAccessToken`→`GitHubReviewException`(401) OR `GitHubReauthRequiredException` both yield the "expired / sign in again" reason and a `FAILED_AT_GITHUB` row.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Extend `gitHubFailureReason(IOException e)` to also match `e instanceof GitHubReauthRequiredException`.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit: `feat(deployment): actionable re-auth message when the GitHub token cannot be refreshed`.

---

### Task 10: Full suite + docs

- [ ] **Step 1:** `server/gradlew -p server :application-server:test` → BUILD SUCCESSFUL.
- [ ] **Step 2:** Update `docs/contributor/keycloak_token_exchange.rst`: replace the "limit the session to 8 hours" note with the new refresh model (Helios seeds via retrieve-token and refreshes against GitHub; requires `read-token`, `GITHUB_CLIENT_SECRET`, `HELIOS_TOKEN_ENCRYPTION_KEY`).
- [ ] **Step 3:** Commit: `docs(auth): document Helios-side GitHub token refresh`.
- [ ] **Step 4:** Open PR to `staging` with the P1–P3 ops checklist in the body.

---

## Self-Review

**Spec coverage:** always-valid token → Tasks 5–8; refresh + rotation → Tasks 6–7; seed via retrieve-token → Tasks 1,5; encryption at rest → Tasks 3–4,7; surface re-auth need → Tasks 6,9 (+ PR #1198); prod config → Task 2 + P1–P3; auto-approval path benefits transparently via Task 8 (no change needed — `ApprovalService` calls the same `GitHubService` method).

**Placeholder scan:** Keycloak request specifics in Task 5 are anchored to Task 1's empirically-confirmed shapes rather than guessed; no "TODO"/"handle errors" placeholders.

**Type consistency:** `GitHubUserTokenRecord(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt)` used identically in Tasks 4–7; `getValidAccessToken(String):String` consistent across Tasks 7–8; `GitHubReauthRequiredException extends IOException` used in Tasks 5,6,8,9.

**Open risk:** Task 1 gates the whole plan. If headless retrieve-token can't yield the refresh token, switch to the event-listener SPI approach (separate plan) before continuing.
