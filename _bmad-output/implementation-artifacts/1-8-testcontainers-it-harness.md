# Story 1.8: Testcontainers Integration Test Harness

Status: ready-for-dev

## Story

As a **twins-mcp maintainer**,
I want **an integration test harness that exercises the running app against a real twins backend in Docker**,
so that **I can catch integration regressions (auth flow, header injection, response shape, pagination) before they reach a release**.

## Acceptance Criteria

1. **AC-1 (Testcontainers boots twins)** — Running `./gradlew integrationTest` starts a twins Docker image via Testcontainers, waits for it to be healthy, and tears it down cleanly on test exit (success or failure). The harness works on the dev OS (Windows 11 per CLAUDE.md) AND CI Linux runners.
2. **AC-2 (EndToEndIT smoke)** — `src/test/java/org/twins/mcp/integration/EndToEndIT.java` boots the application with test env vars (`TWINS_BASE_URL` → container, `TWINS_DOMAIN_ID` seeded, M2M credentials seeded), invokes `list_classes` via the MCP stdio interface, and asserts the response contains the TwinClasses seeded into the test domain.
3. **AC-3 (Gradle task separation)** — `./gradlew build` does NOT trigger integration tests by default. Only `./gradlew integrationTest` (or `./gradlew build -PintegrationTests=true`) runs them. Unit tests (`./gradlew test`) remain fast and hermetic.
4. **AC-4 (M2MAuthFlowIT)** — `src/test/java/org/twins/mcp/integration/M2MAuthFlowIT.java` exercises the full auth path: real POST to `/auth/m2m/token/v1` against the containerised twins → token cached → subsequent `list_classes` call uses it → 401-once path triggers refresh.
5. **AC-5 (fixtures usage)** — Unit tests in `client/endpoint/`, `tool/catalog/` (from Story 1.7) use fixture JSON files from `src/test/resources/fixtures/` with a mocked RestClient. Integration tests use the real container. NO test duplicates fixture-vs-real logic — the boundary is clean.
6. **AC-6 (sanitised failure output)** — Integration test failure output includes the application's sanitised stderr log (proves Story 1.3 sanitiser is wired correctly under failure paths). The failure output NEVER includes the M2M secret or any token value (assert via a test that constructs a failure and asserts the captured stderr).
7. **AC-7 (Gradle config)** — `build.gradle` defines an `integrationTest` source set + task. The test classes follow the `*IT.java` suffix convention. Testcontainers + JUnit 5 platform engine configured.
8. **AC-8 (twins image)** — The twins Docker image is pinned by digest (not just tag) for reproducibility. Image reference declared in `gradle.properties` or `gradle/libs.versions.toml` (e.g., `twins.docker.image: ghcr.io/alcosi/twins:1.4.191@sha256:...`). If no public twins Docker image exists, document the build-from-source path in a comment.

## Tasks / Subtasks

- [ ] **T1 — Gradle integrationTest task** (AC-3, AC-7)
  - [ ] T1.1 In `build.gradle`, define `sourceSets { integrationTest { java.srcDir 'src/integrationTest/java'; resources.srcDir 'src/integrationTest/resources' } }` OR keep ITs in `src/test/java` with a naming convention `*IT.java` filtered via `useJUnitPlatform { includeTags 'integration' }` — pick the convention that matches Boot 4.1 defaults (verify via Context7 MCP)
  - [ ] T1.2 Register `integrationTest` task type `Test`, depends on `testClasses`, runtime classpath = `sourceSets.integrationTest.runtimeClasspath`
  - [ ] T1.3 Ensure `./gradlew build` does NOT run `integrationTest` unless `-PintegrationTests=true` is passed
  - [ ] T1.4 Configure JUnit 5 platform + parallel execution disabled (ITs hit a real container; serialise for stability)
- [ ] **T2 — Testcontainers dep** (AC-1, AC-7)
  - [ ] T2.1 Add to `gradle/libs.versions.toml`: `org.testcontainers:testcontainers`, `org.testcontainers:junit-jupiter`. Version: latest stable (2.x or newer — verify via Context7)
  - [ ] T2.2 Add `org.testcontainers:postgresql` ONLY if twins IT setup needs direct DB seeding (preferred: use twins REST API to seed, not raw SQL — keeps the test independent of twins DB schema)
- [ ] **T3 — Twins container lifecycle** (AC-1, AC-8)
  - [ ] T3.1 Create `src/test/java/org/twins/mcp/integration/TwinsContainer.java` — `@Testcontainers` + `@Container` static `GenericContainer<?>` OR `DockerComposeContainer` (if twins has a published compose file). Verify whether twins publishes a Docker image at the GHCR URL or if it must be built from the `vendor/twins/` submodule
  - [ ] T3.2 Wait strategy: `Wait.forHttp("/actuator/health").forPort(8080)` (or whatever twins exposes — verify in twins source)
  - [ ] T3.3 Expose the twins port to the test via `container.getMappedPort(...)`; set `TWINS_BASE_URL` env on the application under test
  - [ ] T3.4 Seed the test domain: create a domain via twins REST (or pick a seeded test domain), provision an M2M account with `TWIN_CLASS_VIEW` grant, capture its credentials
- [ ] **T4 — EndToEndIT** (AC-2, AC-6)
  - [ ] T4.1 `@SpringBootTest` boots the full app context with test profile
  - [ ] T4.2 Spawn the app as a subprocess (or use `@SpringBootTest` in-process — pick one; subprocess is closer to production reality but harder to assert against)
  - [ ] T4.3 Drive the MCP stdio interface: send JSON-RPC `initialize` → `tools/list` → `tools/call list_classes`
  - [ ] T4.4 Assert response: `structuredContent.classes` non-empty, contains seeded class IDs
  - [ ] T4.5 Capture subprocess stderr; assert it does NOT contain the test M2M secret string
- [ ] **T5 — M2MAuthFlowIT** (AC-4)
  - [ ] T5.1 Subclass or share the container lifecycle from T3
  - [ ] T5.2 First call: trigger `list_classes`; capture via a logging interceptor the `/auth/m2m/token/v1` round-trip
  - [ ] T5.3 Force a 401 by manually invalidating the cached token (`TokenHolder.invalidate()`) and call again → assert refresh happened
- [ ] **T6 — Fixtures usage audit** (AC-5)
  - [ ] T6.1 Verify Story 1.7's unit tests use `MockRestServiceServer` (or equivalent) with the fixture JSON
  - [ ] T6.2 Verify ITs use the container (not fixtures)
  - [ ] T6.3 No overlap: if an IT duplicates a unit-test assertion, delete the duplicate
- [ ] **T7 — Run + verify** (all ACs)
  - [ ] T7.1 `./gradlew integrationTest` green locally (Windows 11 per CLAUDE.md — Docker Desktop required)
  - [ ] T7.2 Document the run command in a README stub
  - [ ] T7.3 If the public twins Docker image is unavailable, document the local build step (`cd vendor/twins && docker build ...`) in the same stub

## Dev Notes

### Architecture patterns and constraints

- **Test layout** (architecture §"Structure Patterns" line 353-356): ITs in `src/test/java/.../integration/`, suffixed `*IT.java`, skipped in default `test` task; run via `integrationTest`.
- **One test class per class** (architecture §"Structure Patterns" line 354): co-located with main; mirror package layout. For ITs that exercise the whole app, one `EndToEndIT` is acceptable as an exception.
- **Sanitiser must hold under failure** (NFR-TM-003): integration tests that intentionally fail must still produce clean stderr — proves Story 1.3's sanitiser isn't bypassed under exception paths.
- **Read-only surface verification** (ARCH-9): the IT proves that `list_classes` is the only tool exposed (no mutating tool accidentally registered). Assert the `tools/list` response includes exactly `list_classes` at this point in the epic.
- **DTOs from `twins-core-dto:1.4.191`** are used to seed via REST (e.g., `TwinClassDTO` in create requests). Do NOT use raw SQL — schema changes in twins would silently break the IT.
- **Windows Docker Desktop** is the dev environment (CLAUDE.md). Testcontainers on Windows requires Docker Desktop's WSL2 backend. CI (Epic 4) runs on Linux.

### Source tree components to touch

- `build.gradle` (integrationTest task + Testcontainers deps)
- `gradle/libs.versions.toml` (Testcontainers versions)
- `src/test/java/org/twins/mcp/integration/TwinsContainer.java` (container lifecycle helper)
- `src/test/java/org/twins/mcp/integration/EndToEndIT.java`
- `src/test/java/org/twins/mcp/integration/M2MAuthFlowIT.java`
- `src/test/resources/application-it.yml` (IT-specific profile)

### Testing standards summary

- JUnit 5 + `@Testcontainers` + `@Container`.
- Tests are tagged `@Tag("integration")` for JUnit platform filtering.
- Container start is slow (~30-60s); reuse across test classes via a shared static container pattern (singleton container, JUnit 5 `@TestInstance(Lifecycle.PER_CLASS)`).
- No parallel execution for ITs (real container, real port allocation).

### Library/framework requirements

| Component | Version | Source |
|---|---|---|
| `org.testcontainers:testcontainers` | 2.x latest | ARCH-SETUP-14 |
| `org.testcontainers:junit-jupiter` | 2.x latest | JUnit 5 integration |
| `twins` Docker image | pinned digest | `vendor/twins/` source or GHCR |
| JUnit 5 | Boot 4.1 default | transitive |

### File structure requirements

- ITs in `org.twins.mcp.integration` package (under `src/test/java/`).
- `application-it.yml` (or `application-test.yml` extended) — separate profile with `twins.connection.base-url` resolved at runtime from the container's mapped port.
- Container lifecycle helper (`TwinsContainer.java`) is reusable across ITs — single point of change.

## Project Structure Notes

- Aligns with architecture §485-611 (`src/test/java/.../integration/` with `M2MAuthFlowIT.java` + `EndToEndIT.java`, fixtures under `src/test/resources/fixtures/`).
- The Testcontainers dep is test-only (`testImplementation` scope) — never leaks into the runtime classpath.
- No conflicts with previous stories.

## References

- Architecture:
  - §"Structure Patterns" line 353-356 (test layout, `*IT.java` convention)
  - §"Project Structure" line 587-610 (full test directory layout including integration/)
  - §"Authentication & Security" line 239-240 (read-only surface — IT verifies)
- Decision log: D12 (read-only service account provisioning), D24 (use `/v2` not `/v1`)
- PRD: NFR-TM-002 (graceful degradation), NFR-TM-003 (secrets — verified under failure paths)
- twins source: `vendor/twins/` — Docker build instructions (if any), health check endpoint, REST API for seeding
- Epics: `_bmad-output/planning-artifacts/epics.md` §"Story 1.8"

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
