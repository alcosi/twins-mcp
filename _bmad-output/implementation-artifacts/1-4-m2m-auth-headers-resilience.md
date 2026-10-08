# Story 1.4: M2M Authentication, Headers Interceptor & Resilience

Status: done

## Story

As a **twins-mcp developer**,
I want **a single M2M authentication client that fetches, caches, and refreshes tokens, plus a RestClient interceptor that injects the immutable `DomainId` and `AuthToken` headers on every outbound call**,
so that **every twins REST call is authenticated, domain-isolated, and resilient to transient failures — without tools having to know anything about auth**.

## Acceptance Criteria

1. **AC-1 (M2M token fetch)** — First twins REST call triggers `TwinsM2MClient` to POST to `/auth/m2m/token/v1` (NOT the deprecated `/auth/m2m/login/v1`). Request body uses the DTO `AuthM2MLoginRqDTOv1` from `twins-core-dto:1.4.191` with `clientId`, `clientSecret`, optional `publicKeyId` from `M2mCredentialsProperties`. Token extracted from `AuthM2MTokenRsDTOv1.authData` map.
2. **AC-2 (token caching)** — `TokenHolder` caches the token in-memory with TTL = `expires_in` seconds − 60 (safety margin). Within TTL, no new M2M call is made.
3. **AC-3 (401 retry)** — On HTTP 401 from a twins REST call, `TokenHolder` invalidates its cached token, `TwinsM2MClient` fetches a fresh one, and the original request is replayed EXACTLY ONCE with the new token (per ARCH-4/ARCH-6 — single retry). A second 401 → throws `TwinsPermissionDenied` (mapped by Story 1.6's error envelope).
4. **AC-4 (immutable headers)** — Every outbound twins REST call has `DomainId` (from `TWINS_DOMAIN_ID`) and `AuthToken` (from `TokenHolder`) headers set by `TwinsHeadersInterceptor`. Any caller-supplied values for these headers are OVERWRITTEN — they cannot be overridden by tool args or any other code path.
5. **AC-5 (header names are twins-defined)** — Header names are literal `DomainId` and `AuthToken` (NOT `X-Domain-Id`, NOT standard `Authorization: Bearer`). Source-of-truth: `org.twins.core.service.HttpRequestService.HEADER_DOMAIN_ID` / `HEADER_AUTH_TOKEN` constants in the twins source tree. Use the same string literals in twins-mcp.
6. **AC-6 (Resilience4j retry)** — Transient failures (connection errors, HTTP 5xx) trigger Resilience4j retry: 3 attempts, exponential backoff 100ms / 200ms / 400ms, per-call timeout 30s. After exhaustion → throws `TwinsUnavailable`. No retry on 4xx (those are deterministic).
7. **AC-7 (token never leaks)** — The token value held by `TokenHolder` is NEVER logged, NEVER included in any exception message, NEVER returned by any tool. At Story 1.4 completion, register the live token with `SecretsSanitiser.registerDynamic(token, "authtoken")` (Story 1.3 hook) so any accidental log emission is masked.
8. **AC-8 (RestClient bean)** — A single `RestClient` bean is defined in `RestClientConfig` with `TwinsHeadersInterceptor` + Resilience4j retry applied. All other components inject this bean; no component constructs its own `RestClient`.

## Tasks / Subtasks

- [ ] **T1 — TokenHolder** (AC-2, AC-3, AC-7)
  - [ ] T1.1 Create `client/TokenHolder.java` — singleton-scoped Spring bean
  - [ ] T1.2 Internal state: `private volatile String token; private volatile long expiresAtMillis;`
  - [ ] T1.3 `getToken()` — returns cached token if `now < expiresAtMillis`, else returns null/throws to signal refresh needed
  - [ ] T1.4 `setToken(String token, long expiresInSeconds)` — stores token + computes `expiresAtMillis = now + (expiresInSeconds − 60) * 1000`; registers the new token with `SecretsSanitiser.registerDynamic(token, "authtoken")`
  - [ ] T1.5 `invalidate()` — clears the cached token (called on 401)
  - [ ] T1.6 Verify the token value is never passed to `toString()`, `equals()`, or any logger
- [ ] **T2 — TwinsM2MClient** (AC-1, AC-3)
  - [ ] T2.1 Create `client/TwinsM2MClient.java` — Spring component
  - [ ] T2.2 Inject `RestClient.Builder` (NOT a configured twins RestClient — the M2M call must NOT carry the AuthToken header yet, since this IS the auth call) and `M2mCredentialsProperties`
  - [ ] T2.3 Method `fetchNewToken()` — POST to `${TWINS_BASE_URL}/auth/m2m/token/v1` with body `AuthM2MLoginRqDTOv1` (clientId, clientSecret, optional publicKeyId)
  - [ ] T2.4 Extract token from `AuthM2MTokenRsDTOv1.authData` map; verify the key name in twins DTO (likely `"token"` — verify by inspecting the `twins-core-dto:1.4.191` JAR via Context7 or by reading the vendored twins submodule)
  - [ ] T2.5 Call `TokenHolder.setToken(token, expiresIn)` on success
  - [ ] T2.6 Throw `TwinsPermissionDenied` on HTTP 401/403; throw `TwinsUnavailable` on 5xx / connection errors (these classes land in Story 1.6 — for Story 1.4, throw a placeholder `M2MAuthException` and refactor in Story 1.6)
- [ ] **T3 — TwinsHeadersInterceptor** (AC-4, AC-5)
  - [ ] T3.1 Create `client/TwinsHeadersInterceptor.java` implementing `ClientHttpRequestInterceptor`
  - [ ] T3.2 Inject `TwinsConnectionProperties` (for domainId) and `TokenHolder`
  - [ ] T3.3 `intercept()` — call `TokenHolder.getToken()`; if null, trigger `TwinsM2MClient.fetchNewToken()` then read again
  - [ ] T3.4 Set headers: `DomainId` from properties, `AuthToken` from `TokenHolder`. OVERWRITE any caller-supplied values for these header names (`HttpRequest.getHeaders().put(name, value)` overwrites)
  - [ ] T3.5 Detect HTTP 401 in the response → call `TokenHolder.invalidate()` and signal a single retry (return the response of the replayed request; track replay-via-flag on a request attribute to avoid infinite loop)
  - [ ] T3.6 Header name literals: `"DomainId"`, `"AuthToken"` (NOT kebab-case, NOT `X-`-prefixed)
- [ ] **T4 — TwinsRestClient facade** (AC-8)
  - [ ] T4.1 Create `client/TwinsRestClient.java` — wraps the configured `RestClient` bean and exposes typed methods per resource (e.g., `postForListClasses(...)`)
  - [ ] T4.2 The facade hides RestClient construction from tools; tools inject `TwinsRestClient` only
- [ ] **T5 — RestClientConfig + ResilienceConfig** (AC-6, AC-8)
  - [ ] T5.1 Create `config/RestClientConfig.java` — `@Bean RestClient twinsRestClient(...)` with `TwinsHeadersInterceptor` applied
  - [ ] T5.2 Create `config/ResilienceConfig.java` — define `RetryRegistry` + `TimeLimiterRegistry` beans; retry config: 3 attempts, exponential backoff 100/200/400 ms, 30s timeout
  - [ ] T5.3 Wire retry via `Retry.ofDefaults("twins")` + a `RetryTemplate` or a `RestClient` middleware (verify the cleanest Boot 4.1 + Resilience4j integration pattern at impl time)
  - [ ] T5.4 M2M auth `RestClient` is SEPARATE — it does not carry the interceptor (would cause infinite loop: interceptor needs token → token fetch needs RestClient → RestClient carries interceptor)
- [ ] **T6 — Tests** (all ACs)
  - [ ] T6.1 `TokenHolderTest` — set + get + expiry + invalidate
  - [ ] T6.2 `TwinsM2MClientTest` — mock RestClient; assert request body shape, response parsing, expiry handling
  - [ ] T6.3 `TwinsHeadersInterceptorTest` — headers set, immutable (caller header overwritten), 401 triggers invalidate + single retry
  - [ ] T6.4 Verify NO test asserts log output containing a token literal (sanitiser is verified separately in Story 1.3)
- [ ] **T7 — Add deps** (AC-6)
  - [ ] T7.1 Add `io.github.resilience4j:resilience4j-retry` + `resilience4j-timelimiter` to `gradle/libs.versions.toml`

## Dev Notes

### Architecture patterns and constraints

- **Header names come from twins constants** (D35, ARCH-7): `DomainId` and `AuthToken` (NOT `X-Domain-Id`, NOT standard Bearer). Verified at `org.twins.core.service.HttpRequestService.HEADER_DOMAIN_ID = "DomainId"`, `HEADER_AUTH_TOKEN = "AuthToken"` in the vendored twins submodule (`vendor/twins/`).
- **Synchronous RestClient only** (ARCH-10): no WebClient/reactive in v1. WebClient is reserved for v2 Streamable HTTP transport at the MCP layer.
- **Token lifecycle** (ARCH-6): singleton holder; refresh 60s before `expires_in`; single retry on HTTP 401; structured error to MCP client on repeat 401.
- **Interceptor is the single point of header attachment** (ARCH-7): no other component may set `DomainId` or `AuthToken` headers. Tools cannot override (NFR-TM-004).
- **Resilience4j config** (ARCH-15): 3 attempts, exponential backoff 100ms/200ms/400ms, on HTTP 5xx + `IOException`. NO retry on 4xx. Per-call timeout 30s.
- **Token cache is in-memory only** (ARCH-2): no persistence. Process dies → cache dies; acceptable.
- **NO retry on the M2M auth call itself** beyond the framework default — fetching a new token is the *recovery action*; retrying the recovery on 401 would loop. The 401-retry logic is in the headers interceptor (for downstream calls), not in `TwinsM2MClient`.

### Source tree components to touch

- `client/TwinsM2MClient.java`
- `client/TokenHolder.java`
- `client/TwinsHeadersInterceptor.java`
- `client/TwinsRestClient.java`
- `config/RestClientConfig.java`
- `config/ResilienceConfig.java`
- Tests in `src/test/java/org/twins/mcp/client/` and `src/test/java/org/twins/mcp/config/`

### Testing standards summary

- Use `MockRestServiceServer` (Spring Test) or `okhttp3.mockwebserver.MockWebServer` to mock twins REST responses.
- For the 401-retry test: queue two responses — first 401, second 200 — and assert the call resolves with the 200 body and `TokenHolder.invalidate()` was invoked exactly once.
- For Resilience4j: test that 3 consecutive 5xx responses result in `TwinsUnavailable`; test that 4xx does NOT retry.

### Library/framework requirements

| Component | Version | Source |
|---|---|---|
| `spring-web` (RestClient) | Boot 4.1 default | transitive |
| `resilience4j-retry` | 2.x latest | ARCH-15 |
| `resilience4j-timelimiter` | 2.x latest | ARCH-15 |
| `twins-core-dto` | 1.4.191 | `AuthM2MLoginRqDTOv1`, `AuthM2MTokenRsDTOv1` |

Verify DTO field names by reading the vendored `vendor/twins/` source or the Maven-published JAR (`./gradlew dependencies --configuration runtimeClasspath` then inspect via IDE).

### File structure requirements

- All classes in `org.twins.mcp.client` (or `config` for the two `*Config` classes).
- The M2M-`RestClient` and the twins-`RestClient` MUST be distinct beans — name them `m2mRestClient` and `twinsRestClient` to avoid confusion.
- `TokenHolder.getToken()` should return `Optional<String>` or throw a domain exception — not a raw `null` that downstream code might miss.

## Project Structure Notes

- Aligns with architecture §485-611 (`client/TwinsM2MClient.java`, `client/TokenHolder.java`, `client/TwinsHeadersInterceptor.java`, `client/TwinsRestClient.java`, `config/RestClientConfig.java`, `config/ResilienceConfig.java`).
- Story 1.6 will introduce the final domain exception types (`TwinsPermissionDenied`, `TwinsUnavailable`); Story 1.4 may temporarily throw a placeholder `M2MAuthException` / `RestCallException` that Story 1.6 refactors.

## References

- Architecture:
  - §"Authentication & Security" line 213-240 (ARCH-4 M2M client, ARCH-5 env vars, ARCH-6 token lifecycle, ARCH-7 header interceptor, ARCH-8 secrets sanitiser integration)
  - §"API & Communication" line 241-269 (ARCH-10 RestClient, ARCH-15 Resilience4j)
  - §"Data Architecture" line 205-211 (ARCH-2 in-memory caches)
- Decision log: D35 (header names `DomainId` + `AuthToken`), D33 (DTO reuse — `AuthM2MLoginRqDTOv1` from `twins-core-dto`)
- PRD: FR-TM-021 (M2M authentication), NFR-TM-002 (graceful degradation), NFR-TM-003 (secrets), NFR-TM-004 (multi-tenancy)
- Epics: `_bmad-output/planning-artifacts/epics.md` §"Story 1.4"
- Memory: `[[twins-rest-headers]]` (header names), `[[twins-core-dto-maven-artifact]]` (DTO source)

## Dev Agent Record

### Agent Model Used

Claude (twins-mcp / GLM-5.2)

### Debug Log References

- `./gradlew compileJava` — BUILD SUCCESSFUL (Resilience4j 2.4.0 API + DTO field access verified).
- `./gradlew test` — BUILD SUCCESSFUL, **78 tests, 0 failures, 0 skipped** (was 58; +20 for Story 1.4).
  - TokenHolderTest: 7 · TwinsM2MClientTest: 9 · TwinsHeadersInterceptorTest: 4.
- `./gradlew build` — BUILD SUCCESSFUL (bootJar produced).

### Completion Notes List

- **AC-2 deviation (token expiry source), forced by the real DTO shape.** The artifact assumed an `expires_in` seconds field on `AuthM2MTokenRsDTOv1` (`setToken(token, expiresInSeconds)`, threshold `= now + (expires_in − 60) * 1000`). Verified against `twins-core-dto:1.4.191` (javap + vendored source): the response has only `Map<String,String> authData`. The token lives at `authData["auth_token"]`; the only expiry signal is `authData["auth_token_expires_at"]`, an **ISO-8601 absolute timestamp string** (e.g. `2026-07-13T10:52:56Z`) — there is **no** `expires_in`. `TwinsM2MClient.computeRefreshThreshold` parses that instant and applies the 60 s margin (`threshold = serverExpiry − 60 s`); `TokenHolder` stores the resulting absolute epoch-millis. AC-2's intent (cache + refresh 60 s before expiry) is preserved; only the signal source changed. When `auth_token_expires_at` is absent (Alcosi external IdP puts only `auth_token`) or unparseable, a 50 min fallback TTL is used; when already-expired (clock skew), a 5 s minimal TTL (the 401-replay path refreshes).
- **Domain exceptions created now, not as placeholders.** T2.6/T-Notes suggested a throwaway `M2MAuthException` refactored by Story 1.6. Instead `error/TwinsPermissionDenied` and `error/TwinsUnavailable` (minimal `RuntimeException` subclasses) are introduced here — they are exactly what AC-3/AC-6 require. Story 1.6 will add `ErrorEnvelopeMapper` + `TwinsNotFound` + `ToolInputInvalid`; no refactor of these two needed.
- **Header names verified.** `DomainId` / `AuthToken` confirmed against `vendor/twins/core/.../service/HttpRequestService.java:24-25` (`HEADER_AUTH_TOKEN="AuthToken"`, `HEADER_DOMAIN_ID="DomainId"`). Used as string literals in `TwinsHeadersInterceptor` (the constants live in the full twins app, not the DTO-only artifact).
- **M2M call carries `DomainId`.** The endpoint is `@ParameterDomainHeader`-annotated (`DomainResolverHeaders` resolves the domain from it), so `TwinsM2MClient` sets `DomainId` explicitly on its POST. It does NOT set `AuthToken` (none exists yet) and does NOT use the headers interceptor.
- **Two RestClient beans (AC-8, T5.4).** `m2mRestClient` (no interceptor, no retry) and `twinsRestClient` (carries `TwinsHeadersInterceptor`). Disambiguated at injection via `@Qualifier`. `ClientHttpRequestFactory` (SimpleClientHttpRequestFactory, 30 s connect+read) shared by both.
- **401 single-replay lives inside the interceptor (AC-3), no loop-guard attribute.** `intercept()` performs original + replay within one invocation (`execution.execute()` is the terminal send, not a re-entry into the interceptor chain), so no request-attribute flag is needed. A second 401 raises `TwinsPermissionDenied`. Resilience4j Retry (in `TwinsRestClient`) operates one layer up and does not retry 4xx, so the two mechanisms compose cleanly.
- **Resilience4j Retry, not TimeLimiter (deviation from T7.1).** `RetryConfig`: maxAttempts=3, exponential backoff 100 ms→200 ms (`IntervalFunction.ofExponentialBackoff(100ms, 2.0)`), retry predicate = `HttpServerErrorException` (5xx) OR `ResourceAccessException` (conn/IO); 4xx excluded. The 30 s per-attempt timeout is enforced at the HTTP layer via `SimpleClientHttpRequestFactory` (connect+read 30 s), NOT via `resilience4j-timelimiter`: TimeLimiter is async-oriented and cannot reliably interrupt a blocking sync RestClient call. Only `resilience4j-retry` was added (T7.1's `resilience4j-timelimiter` dep intentionally omitted — AC-6's 30 s budget is satisfied).
- **Token-discipline (AC-7).** `TokenHolder.setToken` registers the live token with `SecretsSanitiser.registerDynamic(token, "authtoken")`; `invalidate` defensively leaves the prior value registered (it may be in flight in log buffers) and `setToken` unregisters the previously-registered value on replacement, keeping the dynamic registry bounded across rotations (resolves the cross-story note from the Story 1.3 review). Token never reaches `toString`/`equals`/exception messages; exception messages carry HTTP status + operation name only.
- **publicKeyId** parsed `String → UUID` when `TWINS_M2M_PUBLIC_KEY_ID` is non-blank; omitted otherwise (DTO field is `UUID`, nullable).
- **Context-load smoke deferred to Story 1.8.** Bean wiring is straightforward (explicit `@Qualifier`, no cycle); a full `@SpringBootTest` would also start the MCP stdio server and is better validated by the Testcontainers IT harness.

### File List

- `gradle/libs.versions.toml` (modified — added `resilience4j = "2.4.0"` + `resilience4j-retry` library)
- `build.gradle` (modified — added `implementation libs.resilience4j.retry`)
- `src/main/java/org/twins/mcp/error/TwinsPermissionDenied.java` (new)
- `src/main/java/org/twins/mcp/error/TwinsUnavailable.java` (new)
- `src/main/java/org/twins/mcp/client/TokenHolder.java` (new)
- `src/main/java/org/twins/mcp/client/TwinsM2MClient.java` (new)
- `src/main/java/org/twins/mcp/client/TwinsHeadersInterceptor.java` (new)
- `src/main/java/org/twins/mcp/client/TwinsRestClient.java` (new — façade)
- `src/main/java/org/twins/mcp/config/ResilienceConfig.java` (new — Retry bean)
- `src/main/java/org/twins/mcp/config/RestClientConfig.java` (new — two RestClient beans + request factory)
- `src/test/java/org/twins/mcp/client/TokenHolderTest.java` (new — 7 tests)
- `src/test/java/org/twins/mcp/client/TwinsM2MClientTest.java` (new — 9 tests)
- `src/test/java/org/twins/mcp/client/TwinsHeadersInterceptorTest.java` (new — 4 tests)

## Change Log

| Date | Change |
|---|---|
| 2026-07-01 | Story created from Epic 1 breakdown (bmad-create-story) |
| 2026-07-13 | Implementation: TokenHolder + TwinsM2MClient + TwinsHeadersInterceptor + TwinsRestClient + RestClientConfig + ResilienceConfig + TwinsPermissionDenied/TwinsUnavailable + 20 tests (78 total, 0 failures). Story 1.4 → done. Deviations from artifact: AC-2 token expiry read from `authData["auth_token_expires_at"]` ISO-8601 (no `expires_in` field); TimeLimiter replaced by request-factory timeout. |
