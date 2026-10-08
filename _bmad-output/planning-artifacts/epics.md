---
stepsCompleted: [1, 2, 3]
inputDocuments:
  - '_bmad-output/planning-artifacts/prds/prd-twins-mcp-2026-06-23/prd.md'
  - '_bmad-output/planning-artifacts/prds/prd-twins-mcp-2026-06-23/.decision-log.md'
  - '_bmad-output/planning-artifacts/architecture.md'
references:
  twinsSource: 'D:/work/esas/sources/java/twins'
  twinsDtoMaven: 'com.alcosi.twins:twins-core-dto:1.4.191'
project_name: 'twins-mcp'
user_name: 'Nikita'
date: '2026-07-01'
epicsCompleted: []
storiesCompleted: ['1.1', '1.2', '1.3', '1.4', '1.5']
storiesInReview: []
storiesReadyForDev: ['1.6', '1.7', '1.8']
# Reality check 2026-07-13: only stories 1.1–1.3 have code in src/ (verified via
# source tree + test results + artifact Status). 1.1/1.2 Status=done; 1.3 Status=review
# (code review logged in deferred-work.md); 1.4–1.8 Status=ready-for-dev (no code yet).
# Epic 1 is NOT complete — do not treat it as such until 1.4–1.8 are implemented.
---

# twins-mcp - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for twins-mcp, decomposing the requirements from the PRD, UX Design if it exists, and Architecture requirements into implementable stories.

Source-of-truth artifacts:
- PRD: `_bmad-output/planning-artifacts/prds/prd-twins-mcp-2026-06-23/prd.md` (status: final)
- Decision log: same folder, `.decision-log.md` (D1…D34)
- Architecture: `_bmad-output/planning-artifacts/architecture.md` (status: complete)
- twins source tree: `D:/work/esas/sources/java/twins` (sibling repo; consumed at CI time via git-submodule per ARCH-20)
- DTO library: `com.alcosi.twins:twins-core-dto:1.4.191` (Maven Central, Apache 2.0)

UX Design: **N/A** — backend-only MCP server (PRD §2.2, §5 Non-Goals).

## Requirements Inventory

### Functional Requirements

Extracted verbatim from PRD §4. IDs are stable across artifacts (FR-TM-NNN prefix).

- **FR-TM-001** — `list_classes` tool. Agent can list all TwinClasses in the configured domain, paginated, via `POST /private/twin_class/search/v2`. Page size ≤ 100, default 20. Accepts `query`, `page`, `pageSize`. Hybrid response (markdown table + structuredContent JSON with cursor). Empty result → "no TwinClasses match" note, not an error.
- **FR-TM-002** — `describe_class` tool (composite). Agent can get full detail of one TwinClass by `id` or `key`: class metadata via view-by-id + fields via `POST /private/twin_class_fields/search/v2` with `search.twinClassIdMap = {<twinClassId>: true}` and MapperContext query param for detailed field rendering. Realizes UJ-TM-1.
- **FR-TM-003** — `describe_class_relations` tool. Lists inter-class relations (Links) via `POST /private/link/search/v2` with `search.srcOrDstTwinClassIdList = [<twinClassId>]`. Direction arg (`src`/`dst`/`both`, default `both`). Refuses `pageSize > 50` (DTO `@Size(max = 50)` cap). Permission `LINK_VIEW`.
- **FR-TM-004** — `describe_class_statuses` tool. Lists TwinStatus entries via `POST /private/twin_status/search/v2` with `search.twinClassIdMap = {<twinClassId>: true}`. No Twinflow lookup (D24 — entirely v2). Optional `keyLike` filter.
- **FR-TM-010** — `list_glossary_sections` tool. Enumerates distinct glossary sections by aggregating a `section` field over glossary Twins (`POST /private/twin/search/v2` filtered to `TWINS_GLOSSARY` class). Resolves class ID via `list_classes` or `/private/twin_class_by_key/{key}/v1`. Returns "glossary not configured" note if class missing.
- **FR-TM-011** — `get_glossary_section` tool. Fetches all glossary entries of one section. Filters by `twinClassIdList` + section value via twin's serialized field data.
- **FR-TM-012** — `get_glossary_term` tool. Fetches one glossary entry by term name (case-insensitive, partial match via `twinNameLikeList`). Returns best match or zero-match with "did you mean…?" suggestions.
- **FR-TM-020** — Connection configuration. Process reads from env vars at start: `TWINS_BASE_URL`, `TWINS_DOMAIN_ID`, `TWINS_M2M_CLIENT_ID`, `TWINS_M2M_CLIENT_SECRET`, optional `TWINS_M2M_PUBLIC_KEY_ID`. (Corrected per ARCH-5; PRD body has stale `TWINS_M2M_LOGIN`/`TWINS_M2M_SECRET` — PM-action PG1.)
- **FR-TM-021** — M2M authentication. Authenticates via `POST /auth/m2m/token/v1` (NOT the deprecated `/auth/m2m/login/v1`). Token cached in memory only; never logged; refreshed before expiry; single retry on 401.
- **FR-TM-022** — Stdio transport (v1). Reads JSON-RPC from stdin, writes to stdout, logs to stderr (never stdout). Exits cleanly on SIGTERM/Ctrl-C. Streamable HTTP transport reserved for v2.
- **FR-TM-040** — Hybrid response shape. Every tool response: (1) markdown summary (5–30 lines, ≤30% of structured JSON), (2) structuredContent JSON matching twins DTO field names. Total response ≤ 32 KiB; over-cap paginates.
- **FR-TM-041** — Pagination. Opaque cursor returned in both markdown ("next page: pass cursor=…") and structured JSON (`nextCursor`). Cursor passed back unchanged. Tools refuse `pageSize > 100`; default 20.
- **FR-TM-042** — Output sanitization. No tool response, log line, or error message contains: `TWINS_M2M_CLIENT_SECRET`, `AuthToken` values (or any token-shaped string — JWT, opaque-base64), or any sensitive DTO field (`@Schema(accessMode = READ_ONLY)` followed).
- **FR-TM-050** — Smithery listing. `twins-mcp` listed on Smithery with install metadata, env-var schema, one-paragraph description. `smithery.json` (or equivalent) committed to repo.
- **FR-TM-051** — `claude mcp add` snippet in README. Snippet runs as-is on a fresh shell with Java 25 installed. Documents Java 25 dependency + minimum twins REST API version.
- **FR-TM-052** — JSON config block for `claude_desktop_config.json`. Manual install path for users who don't use `claude mcp add`.
- **FR-TM-053** — GitHub release artifacts. Each release: fat JAR, Docker image (`bootBuildImage` per ARCH-19), SHA-256 checksum. Release notes document env vars, breaking changes, twins REST API version compat.
- **FR-TM-060** — Module layout. Standalone Gradle build (D32 overrides the original "submodule inside twins" wording). Distribution artifact: fat JAR via `bootJar`.
- **FR-TM-061** — Spring AI MCP server starter usage. Module uses `org.springframework.ai:spring-ai-starter-mcp-server` (modern name; legacy `spring-ai-mcp-server-spring-boot-starter` abandoned at 1.0.0-M6).
- **FR-TM-062** — Reuse of twins components (partial per D33). DTO classes imported from `com.alcosi.twins:twins-core-dto:1.4.191` (Maven Central). Auth client, REST client, OpenAPI config, `ApiUser` thread-local, `findEntitySafe()`/`isEntityReadDenied()` gate NOT reused — reimplemented in twins-mcp or applied server-side via `@ProtectedBy`.

### NonFunctional Requirements

Extracted from PRD §Cross-Cutting NFRs. IDs `NFR-TM-NNN`.

- **NFR-TM-001 (Performance)** — `list_classes` p95 ≤ 2 s; `describe_class*` p95 ≤ 3 s; glossary tools p95 ≤ 500 ms. Warm REST connection, ≤ 100 TwinClasses. (Note: ARCH-3 keeps TwinClass metadata cache off in v1 — monitor.)
- **NFR-TM-002 (Reliability)** — Graceful degradation when twins unreachable: structured error to MCP client, no crash, automatic retry on next tool call.
- **NFR-TM-003 (Security — secrets)** — Credentials, M2M tokens, the `AuthToken` value (and any token-shaped string) never in logs, tool responses, error messages, or stack traces. Sanitiser enforces.
- **NFR-TM-004 (Security — multi-tenancy)** — All REST calls include `DomainId` header from env. No tool can override. The `AuthToken` header is similarly immutable from tool args (set by interceptor from `TokenHolder`).
- **NFR-TM-005 (Observability)** — Structured JSON logs to stderr. Basic local metrics (counters per tool, latency histogram). No remote telemetry in v1.
- **NFR-TM-006 (Compatibility)** — Java 25 LTS (no `--enable-preview` per D34). Spring Boot 4.1+. MCP spec 2025-06-18.
- **NFR-TM-007 (Backward compatibility)** — Per-endpoint version pinning (v1/v2). Tracks deprecations.
- **NFR-TM-008 (OSS ergonomics)** — Clone-to-first-`list_classes` ≤ 10 min for a new user with Java 25 installed, following README only.

### Additional Requirements

Derived from Architecture document — work items beyond FRs that need stories.

**Starter Template (Epic 1 Story 1 — per Step 1 §5 of workflow):**

- **ARCH-STARTER-1** — Project initialization. New Gradle build at repo root with: Boot 4.1.0 plugin, Spring AI 2.0.0 BOM, `io.spring.dependency-management` 1.1.8, Java 25 toolchain (no `--enable-preview`), dep `com.alcosi.twins:twins-core-dto:1.4.191`, dep `org.springframework.ai:spring-ai-starter-mcp-server`. Skeleton package layout (`app/`, `config/`, `client/`, `tool/`, `response/`, `error/`, `secrets/`, `metrics/`). `Application.java` with `@SpringBootApplication`. Verifies OQ-ARCH-1 (Java 25 compile of twins-core-dto) on first build. Git submodule `vendor/twins/` pinned.

**Cross-Cutting Setup (Architectural Decisions ARCH-1…24 that aren't directly FRs):**

- **ARCH-SETUP-2** — `app/StartupEnvValidator` — fail-fast on missing/malformed env vars (ARCH-5). Validates `TWINS_BASE_URL` reachability with short timeout.
- **ARCH-SETUP-3** — `config/RestClientConfig` + `client/TwinsHeadersInterceptor` — RestClient bean; interceptor injects both twins-mandated headers on every outbound call: `DomainId` from env (`TWINS_DOMAIN_ID`) and `AuthToken` from `TokenHolder`. Tools cannot override either. Resilience4j config (retry 3 attempts, exponential backoff, 30 s timeout) per ARCH-15. Header names from `org.twins.core.service.HttpRequestService` constants.
- **ARCH-SETUP-4** — `client/TwinsM2MClient` + `TokenHolder` — M2M auth via `POST /auth/m2m/token/v1`, extract token from `authData` map, in-memory cache with TTL = `expires_in` − 60 s, single retry on 401 per ARCH-4 + ARCH-6. Token is transported on every subsequent call via the `AuthToken` header (NOT standard `Authorization: Bearer`).
- **ARCH-SETUP-5** — `secrets/SecretsSanitiser` — Logback filter pattern-based redaction (token-shaped strings: JWT structure, opaque-base64, Bearer-prefix patterns; base64 blobs ≥32 chars; literal `TWINS_M2M_CLIENT_SECRET` value; literal in-memory `AuthToken` value) per ARCH-8. Wired into `logback-spring.xml`.
- **ARCH-SETUP-6** — `tool/ToolRegistry` + `tool/ToolAllowlist` + `tool/ToolKey` enum — startup enforcement of read-only surface per ARCH-9. Non-allowlisted bean ⇒ startup failure.
- **ARCH-SETUP-7** — `tool/ToolArgsValidator` — JSR-380 hook on every tool call per Process Pattern (Step 5).
- **ARCH-SETUP-8** — `response/ToolResponseBuilder` + `CursorCodec` + `MarkdownSummariser` + `SizeCapEnforcer` (32 KiB) per ARCH-13/14/16.
- **ARCH-SETUP-9** — `error/ErrorEnvelopeMapper` + domain exceptions (`TwinsPermissionDenied`, `TwinsNotFound`, `TwinsUnavailable`, `ToolInputInvalid`) per ARCH-12.
- **ARCH-SETUP-10** — `config/McpServerConfig` — MCP server bean registration via `spring-ai-starter-mcp-server` per FR-TM-061.
- **ARCH-SETUP-11** — `config/LoggingConfig` + `resources/logback-spring.xml` — JSON encoder (Logback built-in preferred; fall back to `logstash-logback-encoder` per G7), stderr output only, sanitiser filter attached per ARCH-21.
- **ARCH-SETUP-12** — `config/MetricsConfig` + `metrics/ToolMetrics` + `metrics/MetricsEmitOnShutdown` — Micrometer counters + latency histogram; metrics emitted as a single log line on graceful shutdown per ARCH-22.
- **ARCH-SETUP-13** — `client/endpoint/*` wrappers — one per twins REST resource used: `TwinClassSearchEndpoint`, `TwinClassFieldSearchEndpoint`, `LinkSearchEndpoint`, `TwinStatusSearchEndpoint`, `TwinSearchEndpoint` (glossary Twins).
- **ARCH-SETUP-14** — Testcontainers integration test harness — `*IT.java` suffix, `integrationTest` Gradle task, uses twins Docker image per G6.

**Distribution & CI (Architecture §Infrastructure):**

- **ARCH-SETUP-15** — `claude.json` manifest at repo root — MCP install metadata for Claude (format spec at story time per G2).
- **ARCH-SETUP-16** — `smithery.json` manifest at repo root — Smithery listing per FR-TM-050 (format spec at story time per G3).
- **ARCH-SETUP-17** — `README.md` — install snippet (`claude mcp add`), env var table, JSON config block, Java 25 prerequisite, twins REST API version compat per FR-TM-051 + FR-TM-052.
- **ARCH-SETUP-18** — `.github/workflows/ci.yml` — build + test + Spotless/Checkstyle on push per Pattern Enforcement.
- **ARCH-SETUP-19** — `.github/workflows/release.yml` — tag → GitHub release with fat JAR + Docker image (`bootBuildImage`) + SHA-256 per FR-TM-053 + ARCH-19.
- **ARCH-SETUP-20** — `.github/workflows/check-deprecated.yml` + `scripts/check-deprecated-endpoints.sh` + `docs/allowed-endpoints.md` + `vendor/twins/` submodule — no-deprecated-endpoints CI gate per ARCH-20. Initial inventory: 5 v1 endpoints.

**Provisioning documentation:**

- **ARCH-SETUP-21** — `docs/provisioning-guide.md` — recommended `TWINS_GLOSSARY` TwinClass schema (ARCH-24 / OQ-PRD-6) + section grouping mechanism (OQ-PRD-7) + service-account read-only grant checklist (D12).

### UX Design Requirements

**N/A.** twins-mcp is a backend-only MCP server with no UI surface (PRD §2.2, §5 Non-Goals). UX-DR extraction skipped per step-1 §6 allowance.

### FR Coverage Map

**Functional Requirements (20/20 covered):**

| FR | Epic | Description |
|---|---|---|
| FR-TM-001 | Epic 1 | `list_classes` — first working tool, end-to-end proof of architecture |
| FR-TM-002 | Epic 2 | `describe_class` (composite: metadata + fields) |
| FR-TM-003 | Epic 2 | `describe_class_relations` |
| FR-TM-004 | Epic 2 | `describe_class_statuses` |
| FR-TM-010 | Epic 3 | `list_glossary_sections` |
| FR-TM-011 | Epic 3 | `get_glossary_section` |
| FR-TM-012 | Epic 3 | `get_glossary_term` |
| FR-TM-020 | Epic 1 | Connection configuration (env vars per ARCH-5) |
| FR-TM-021 | Epic 1 | M2M authentication (POST `/auth/m2m/token/v1`; token via `AuthToken` header per D35) |
| FR-TM-022 | Epic 1 | Stdio transport via `spring-ai-starter-mcp-server` |
| FR-TM-040 | Epic 1 | Hybrid response shape (markdown + structuredContent) |
| FR-TM-041 | Epic 1 | Pagination (opaque cursor) |
| FR-TM-042 | Epic 1 | Output sanitization (secrets sanitiser) |
| FR-TM-050 | Epic 4 | Smithery listing |
| FR-TM-051 | Epic 4 | `claude mcp add` snippet in README |
| FR-TM-052 | Epic 4 | JSON config block for `claude_desktop_config.json` |
| FR-TM-053 | Epic 4 | GitHub release artifacts (fat JAR + Docker + SHA-256) |
| FR-TM-060 | Epic 1 | Module layout (standalone build per D32) |
| FR-TM-061 | Epic 1 | Spring AI MCP server starter usage |
| FR-TM-062 | Epic 1 | DTO reuse via `twins-core-dto` (per D33) |

**Non-Functional Requirements (8/8 covered):**

| NFR | Epic(s) | Coverage |
|---|---|---|
| NFR-TM-001 Performance (p95 budgets) | 1, 2, 3 | Per-tool latency budgets verified in each tool's epic |
| NFR-TM-002 Reliability | 1 | Error envelope + Resilience4j retry in foundation |
| NFR-TM-003 Secrets | 1 | Secrets sanitiser wired into Logback in foundation |
| NFR-TM-004 Multi-tenancy | 1 | `TwinsHeadersInterceptor` (DomainId immutable) in foundation |
| NFR-TM-005 Observability | 1 (logging) + 4 (metrics) | Logging in foundation; metrics added in distribution epic |
| NFR-TM-006 Compatibility | 1 | Version pins per D34 enforced from project init |
| NFR-TM-007 Backward compat | 4 | CI no-deprecated-endpoints gate (ARCH-20) |
| NFR-TM-008 OSS ergonomics | 4 | README install path verified in distribution epic |

**Setup Items (21/21 covered):**

| Setup | Epic |
|---|---|
| ARCH-STARTER-1 (project init) | 1 |
| ARCH-SETUP-2 (env validator) | 1 |
| ARCH-SETUP-3 (RestClient + TwinsHeadersInterceptor + Resilience) | 1 |
| ARCH-SETUP-4 (TwinsM2MClient + TokenHolder) | 1 |
| ARCH-SETUP-5 (SecretsSanitiser) | 1 |
| ARCH-SETUP-6 (ToolRegistry + Allowlist) | 1 |
| ARCH-SETUP-7 (ToolArgsValidator) | 1 |
| ARCH-SETUP-8 (ToolResponseBuilder + cursor + size cap) | 1 |
| ARCH-SETUP-9 (Error envelope + domain exceptions) | 1 |
| ARCH-SETUP-10 (McpServerConfig) | 1 |
| ARCH-SETUP-11 (LoggingConfig + logback-spring.xml) | 1 |
| ARCH-SETUP-12 (Micrometer metrics) | 4 |
| ARCH-SETUP-13 (endpoint wrappers) | 1 (TwinClassSearchEndpoint), 2 (Field/Link/Status endpoints), 3 (TwinSearchEndpoint) |
| ARCH-SETUP-14 (Testcontainers harness) | 1 |
| ARCH-SETUP-15 (claude.json) | 4 |
| ARCH-SETUP-16 (smithery.json) | 4 |
| ARCH-SETUP-17 (README) | 4 |
| ARCH-SETUP-18 (ci.yml) | 4 |
| ARCH-SETUP-19 (release.yml) | 4 |
| ARCH-SETUP-20 (check-deprecated.yml + submodule + inventory) | 4 |
| ARCH-SETUP-21 (provisioning-guide.md) | 3 |

**Totals:** 20/20 FR ✅ · 8/8 NFR ✅ · 21/21 Setup ✅ · 49/49 work-items covered.

## Epic List

### Epic 1: Walking Skeleton — `list_classes` works end-to-end

**Goal:** Operator can run twins-mcp, connect via `claude mcp add`, ask "what classes are in this domain?", and get a grounded paginated answer. This epic delivers one fully working tool plus all shared infrastructure, proving the architecture end-to-end.

**Standalone:** Yes. One MCP tool that works end-to-end; subsequent epics build on this foundation.

**FRs covered:** FR-TM-001, FR-TM-020, FR-TM-021, FR-TM-022, FR-TM-040, FR-TM-041, FR-TM-042, FR-TM-060, FR-TM-061, FR-TM-062.
**NFRs covered:** NFR-TM-001 (list_classes p95 ≤ 2 s), NFR-TM-002, NFR-TM-003, NFR-TM-004, NFR-TM-005 (logging half), NFR-TM-006.
**Setup items:** ARCH-STARTER-1, ARCH-SETUP-2, ARCH-SETUP-3, ARCH-SETUP-4, ARCH-SETUP-5, ARCH-SETUP-6, ARCH-SETUP-7, ARCH-SETUP-8, ARCH-SETUP-9, ARCH-SETUP-10, ARCH-SETUP-11, ARCH-SETUP-13 (TwinClassSearchEndpoint), ARCH-SETUP-14.

### Epic 2: Domain Catalog Drill-Down

**Goal:** Operator can drill into a specific TwinClass — get its fields, inter-class relations, and possible statuses. Adds the three remaining catalog tools on top of Epic 1's infrastructure.

**Standalone:** Yes. Builds on Epic 1; each new tool lives in its own file under `tool/catalog/`, so parallel agent work has zero merge conflicts.

**FRs covered:** FR-TM-002 (`describe_class` composite — class metadata + fields), FR-TM-003 (`describe_class_relations`), FR-TM-004 (`describe_class_statuses`).
**NFRs covered:** NFR-TM-001 (describe_class* p95 ≤ 3 s).
**Setup items:** ARCH-SETUP-13 (TwinClassFieldSearchEndpoint, LinkSearchEndpoint, TwinStatusSearchEndpoint).

### Epic 3: Terminology — Glossary Tools

**Goal:** Operator can ask "what does *Twinflow* mean?" and get the canonical glossary definition grounded in the `TWINS_GLOSSARY` Twins of the configured domain. Adds the three glossary tools plus the operator-facing provisioning guide.

**Standalone:** Yes. Builds on Epic 1; tools live under `tool/glossary/`. Provisioning guide is a doc deliverable that decouples from Epic 2.

**FRs covered:** FR-TM-010, FR-TM-011, FR-TM-012.
**NFRs covered:** NFR-TM-001 (glossary p95 ≤ 500 ms).
**Setup items:** ARCH-SETUP-13 (TwinSearchEndpoint), ARCH-SETUP-21 (provisioning-guide.md).

### Epic 4: Ship v1 — Distribution, CI, Observability

**Goal:** External OSS contributor can clone the repo, install via `claude mcp add` in under 10 minutes, and use twins-mcp. Releases publish automatically with a CI gate that prevents deprecated twins endpoints from leaking in.

**Standalone:** Yes, per component. Functionally, distribution is most useful after Epics 1–3 deliver tools to distribute, but each Epic 4 component (manifests, CI workflows, metrics) works on its own.

**FRs covered:** FR-TM-050, FR-TM-051, FR-TM-052, FR-TM-053.
**NFRs covered:** NFR-TM-005 (metrics half), NFR-TM-007 (backward compat via CI gate), NFR-TM-008 (OSS ergonomics — clone-to-first-`list_classes` ≤ 10 min).
**Setup items:** ARCH-SETUP-12, ARCH-SETUP-15, ARCH-SETUP-16, ARCH-SETUP-17, ARCH-SETUP-18, ARCH-SETUP-19, ARCH-SETUP-20.

### Dependency Graph

```
Epic 1 (foundation + list_classes)
  ├── Epic 2 (catalog drill-down)     ─┐
  ├── Epic 3 (terminology)             ─┤  parallel after Epic 1
  └── Epic 4 (distribution + CI)       ─┘  (distribution wants 1–3 first; CI/manifests stand alone)
```

### Story Dependency Within Epic 1

```
1.1 (project init)
  └─ 1.2 (config + env validator)
        ├─ 1.3 (logging + sanitiser)     ─┐ parallel after 1.2
        └─ 1.4 (M2M + headers + retry)   ─┘
              └─ 1.5 (tool foundation)
                    └─ 1.6 (error envelope)
                          └─ 1.7 (list_classes end-to-end)
                                └─ 1.8 (Testcontainers IT)
```

## Epic 1: Walking Skeleton — `list_classes` works end-to-end

**Goal:** Operator can run twins-mcp, connect via `claude mcp add`, ask "what classes are in this domain?", and get a grounded paginated answer. Delivers one fully working tool plus all shared infrastructure, proving the architecture end-to-end.

**FRs covered:** FR-TM-001, FR-TM-020, FR-TM-021, FR-TM-022, FR-TM-040, FR-TM-041, FR-TM-042, FR-TM-060, FR-TM-061, FR-TM-062.
**NFRs covered:** NFR-TM-001 (list_classes p95 ≤ 2 s), NFR-TM-002, NFR-TM-003, NFR-TM-004, NFR-TM-005 (logging half), NFR-TM-006.
**Setup items:** ARCH-STARTER-1, ARCH-SETUP-2, ARCH-SETUP-3, ARCH-SETUP-4, ARCH-SETUP-5, ARCH-SETUP-6, ARCH-SETUP-7, ARCH-SETUP-8, ARCH-SETUP-9, ARCH-SETUP-10, ARCH-SETUP-11, ARCH-SETUP-13 (TwinClassSearchEndpoint), ARCH-SETUP-14.

### Story 1.1: Project Initialization & Build Skeleton

As a **developer**,
I want **a clean Gradle project that builds and boots a Spring Boot application with all required dependencies**,
So that **subsequent stories have a stable foundation to add MCP tooling on top of**.

**Acceptance Criteria:**

**Given** a fresh clone of the `twins-mcp` repository on a machine with Java 25 installed
**When** the developer runs `./gradlew build`
**Then** the build succeeds with zero compile errors
**And** a `bootJar` fat JAR is produced in `build/libs/twins-mcp-<version>.jar`
**And** the `vendor/twins/` git submodule is initialized (per ARCH-20 staging)

**Given** the build is up to date
**When** the developer runs `./gradlew bootRun` with valid env vars (TWINS_BASE_URL, TWINS_DOMAIN_ID, TWINS_M2M_CLIENT_ID, TWINS_M2M_CLIENT_SECRET)
**Then** the application starts and logs `Started Application` to stderr
**And** no logs are emitted to stdout (preserves stdio transport channel)

**Given** `build.gradle` is inspected
**When** reviewing dependencies and plugins
**Then** Boot 4.1.x plugin, Spring AI 2.0.0 BOM via `io.spring.dependency-management` 1.1.8+, Java 25 toolchain (no `--enable-preview` per D34), `org.springframework.ai:spring-ai-starter-mcp-server`, and `com.alcosi.twins:twins-core-dto:1.4.191` are all declared
**And** a version catalog exists at `gradle/libs.versions.toml` per Pattern Enforcement

**Given** the package layout under `src/main/java/org/twins/mcp/`
**When** reviewed
**Then** the skeleton packages exist (even if empty): `app/`, `config/`, `client/`, `client/endpoint/`, `tool/`, `tool/catalog/`, `tool/glossary/`, `response/`, `error/`, `secrets/`, `metrics/`
**And** `app/Application.java` contains `@SpringBootApplication` and a `main()` method

### Story 1.2: Connection Configuration & Startup Environment Validation

As an **operator deploying twins-mcp**,
I want **the application to fail fast with a clear error when required environment variables are missing or malformed**,
So that **I don't waste debugging time on silent misconfigurations and never leak the wrong DomainId or credentials to twins**.

**Acceptance Criteria:**

**Given** env vars TWINS_BASE_URL, TWINS_DOMAIN_ID, TWINS_M2M_CLIENT_ID, TWINS_M2M_CLIENT_SECRET are set to valid values
**When** the application starts
**Then** `StartupEnvValidator` validates all four are present and non-empty
**And** `TWINS_BASE_URL` is parsed as a valid URL with `http` or `https` scheme
**And** startup completes normally

**Given** any of the four required env vars is missing or blank
**When** the application starts
**Then** the application exits with non-zero status before the Spring context finishes refreshing
**And** a structured JSON error is logged to stderr naming the missing var
**And** the error message does NOT include the value of any other env var (especially not the secret)

**Given** `TWINS_BASE_URL` is malformed (e.g., "not-a-url")
**When** the application starts
**Then** startup fails fast with a clear validation error
**And** no HTTP request is attempted against twins

**Given** `TwinsConnectionProperties` and `M2mCredentialsProperties` are inspected
**When** reviewed
**Then** `TWINS_M2M_PUBLIC_KEY_ID` is optional (nullable) and the other four are `@NotBlank`
**And** the properties classes use JSR-380 `@Validated` with `@ConfigurationProperties`

### Story 1.3: Structured Logging & Secrets Sanitiser

As a **security-conscious operator**,
I want **all logs to be emitted as structured JSON to stderr with token-shaped values redacted**,
So that **M2M tokens, the `AuthToken` value, and `TWINS_M2M_CLIENT_SECRET` can never leak via log files or stderr captures**.

**Acceptance Criteria:**

**Given** the application is running
**When** any code path emits a log message via SLF4J
**Then** the line on stderr is a single JSON object with `timestamp`, `level`, `logger`, `message`, and (when present) structured fields
**And** stdout receives zero log lines (preserves stdio transport)

**Given** a log statement includes a JWT-shaped string (three base64 segments separated by dots, ≥ 32 chars total)
**When** the sanitiser filter runs
**Then** the value is replaced with `[REDACTED:jwt]` in the JSON output
**And** the original token never appears in the rendered line

**Given** a log statement includes the literal value of `TWINS_M2M_CLIENT_SECRET` (read at startup)
**When** the sanitiser filter runs
**Then** the value is replaced with `[REDACTED:secret]`
**And** case-insensitive matching applies

**Given** a log statement includes a base64 blob ≥ 32 chars OR a string starting with `Bearer `
**When** the sanitiser filter runs
**Then** the value is replaced with `[REDACTED:token]`

**Given** `logback-spring.xml` is inspected
**When** reviewed
**Then** a `<filter>` for `SecretsSanitiser` is wired into the root appender
**And** the encoder is Logback's built-in JSON encoder (or `logstash-logback-encoder` as fallback per G7)
**And** the appender target is stderr only

### Story 1.4: M2M Authentication, Headers Interceptor & Resilience

As a **twins-mcp developer**,
I want **a single M2M authentication client that fetches, caches, and refreshes tokens, plus a RestClient interceptor that injects the immutable `DomainId` and `AuthToken` headers on every outbound call**,
So that **every twins REST call is authenticated, domain-isolated, and resilient to transient failures — without tools having to know anything about auth**.

**Acceptance Criteria:**

**Given** valid M2M credentials in env
**When** the first twins REST call is made
**Then** `TwinsM2MClient` POSTs to `/auth/m2m/token/v1` (NOT `/auth/m2m/login/v1`)
**And** extracts the token from the `authData` field of the response
**And** caches the token in `TokenHolder` with TTL = `expires_in` seconds − 60

**Given** a cached token exists and has not expired
**When** a twins REST call is made
**Then** no new M2M auth request is issued
**And** the cached token is used

**Given** a twins REST call returns HTTP 401
**When** the call is retried (single retry per ARCH-4)
**Then** `TokenHolder` invalidates its cached token
**And** `TwinsM2MClient` fetches a fresh token
**And** the original request is replayed exactly once with the new token

**Given** any outbound twins REST call
**When** intercepted by `TwinsHeadersInterceptor`
**Then** the `DomainId` header is set from `TWINS_DOMAIN_ID` env var
**And** the `AuthToken` header is set from `TokenHolder` (NOT `Authorization: Bearer`)
**And** any caller-supplied values for these headers are overwritten (immutable)

**Given** the twins backend is transiently unavailable
**When** a REST call fails with a connection error or 5xx
**Then** Resilience4j retries up to 3 times with exponential backoff
**And** after exhausting retries, throws `TwinsUnavailable` (mapped by `ErrorEnvelopeMapper`)
**And** the call times out at 30 s max per attempt

**Given** `TokenHolder` caches a token
**When** the token value is read by `TwinsHeadersInterceptor`
**Then** the token is never logged, never included in any exception message, and never returned by any tool

### Story 1.5: Tool Foundation — Registry, Allowlist, Validator, Response Pipeline

As a **future tool author** (in Epics 2/3),
I want **shared infrastructure for tool registration, input validation, and hybrid response shaping**,
So that **adding a new tool is a small, well-defined task and every tool response has the same shape, size cap, and pagination contract**.

**Acceptance Criteria:**

**Given** a Spring bean implements `TwinsMcpTool` and its `key()` returns a value in `ToolKey` enum
**When** the application starts
**Then** `ToolRegistry` discovers the bean and registers it under its key
**And** `ToolAllowlist` verifies the key is in the enum (startup fails if a tool's key is not allowlisted)

**Given** a Spring bean implements `TwinsMcpTool` but its key is not in `ToolKey`
**When** the application starts
**Then** startup fails fast with a clear error naming the offending bean and its key

**Given** a tool is invoked with input args
**When** `ToolArgsValidator` runs
**Then** JSR-380 constraints on the args object are enforced
**And** violations result in `ToolInputInvalid` (mapped by `ErrorEnvelopeMapper`) — not a generic 500

**Given** a tool produces a result (list, detail, etc.)
**When** `ToolResponseBuilder` assembles the response
**Then** the response contains a markdown summary (5–30 lines, ≤ 30% of structured JSON size)
**And** the response contains `structuredContent` JSON matching twins DTO field names
**And** the total response size is ≤ 32 KiB; if it would exceed, pagination cursor is returned instead

**Given** a tool returns paginated data
**When** the next page exists
**Then** the response includes an opaque `nextCursor` in structuredContent
**And** the markdown summary includes a hint like `next page: pass cursor=…`
**And** `CursorCodec` round-trips the cursor (encode → decode → identical payload)

**Given** a tool receives `pageSize` argument
**When** pageSize > 100
**Then** the call is rejected with `ToolInputInvalid`
**And** default pageSize is 20 when not specified

**Given** `McpServerConfig` is loaded
**When** the application starts
**Then** the Spring AI MCP server bean is registered
**And** the server is configured for stdio transport (reads JSON-RPC from stdin, writes to stdout)

### Story 1.6: Error Envelope & Domain Exceptions

As a **MCP client (e.g., Claude)**,
I want **consistent, sanitized error envelopes regardless of the underlying failure mode**,
So that **I can reliably surface errors to the user without leaking secrets, stack traces, or twins-internal details**.

**Acceptance Criteria:**

**Given** any tool throws `TwinsPermissionDenied`
**When** `ErrorEnvelopeMapper` handles the exception
**Then** the MCP client receives an error envelope with code `TWINS_PERMISSION_DENIED`, a human-readable message, and no stack trace
**And** the underlying twins 403 response body is not echoed

**Given** a tool throws `TwinsNotFound`
**When** mapped
**Then** the envelope code is `TWINS_NOT_FOUND` and the message identifies which resource class was queried

**Given** twins is unreachable or returns 5xx after Resilience4j retries
**When** `TwinsUnavailable` is thrown and mapped
**Then** the envelope code is `TWINS_UNAVAILABLE` and the message names the operation attempted (e.g., `list_classes`)

**Given** a tool input fails JSR-380 validation
**When** `ToolInputInvalid` is thrown and mapped
**Then** the envelope code is `TOOL_INPUT_INVALID` and the message lists each constraint violation

**Given** any other uncaught exception reaches the mapper
**When** mapped
**Then** the envelope code is `INTERNAL_ERROR` and the message is generic ("An internal error occurred")
**And** the full stack trace is logged at ERROR level to stderr (sanitised) but NOT included in the envelope

**Given** the `AuthToken` value or `TWINS_M2M_CLIENT_SECRET` value appears anywhere in an exception message or cause
**When** mapped
**Then** the sanitised envelope does NOT contain those values
**And** the logged stack trace also has those values redacted by the secrets sanitiser

### Story 1.7: `list_classes` Tool End-to-End

As a **MCP client (e.g., Claude) connected via stdio**,
I want **a `list_classes` tool that lists TwinClasses in the configured domain, paginated, with markdown + structured JSON output**,
So that **I can answer the user's question "what entity types exist in this domain?" with grounded data**.

**Acceptance Criteria:**

**Given** twins is reachable and the configured domain has TwinClasses
**When** the MCP client calls `list_classes` with no args
**Then** the tool POSTs to `/private/twin_class/search/v2` with `query: null`, `page: 0`, `pageSize: 20`
**And** the response is a hybrid: markdown table (class key, name, id, brief) + structuredContent JSON
**And** the response size is ≤ 32 KiB

**Given** more than 20 TwinClasses exist
**When** `list_classes` is called with default pagination
**Then** the response includes `nextCursor` in structuredContent
**And** the markdown summary notes the cursor for the next page
**And** passing the cursor as `cursor=<value>` on the next call returns the next page

**Given** a tool input with `pageSize: 200`
**When** `list_classes` is called
**Then** the call is rejected with `TOOL_INPUT_INVALID` and a message naming the cap (100)

**Given** the configured domain has zero TwinClasses
**When** `list_classes` is called
**Then** the response is NOT an error; the markdown summary says "no TwinClasses match"
**And** the structuredContent has an empty `classes` array

**Given** twins returns 403 for the configured DomainId
**When** `list_classes` is called
**Then** the response envelope code is `TWINS_PERMISSION_DENIED`
**And** the operator is informed (via message) that the M2M account may lack `TWIN_CLASS_VIEW`

**Given** a real MCP client connected via `claude mcp add` (stdio transport)
**When** the client sends the JSON-RPC `tools/call` for `list_classes`
**Then** the response is delivered on stdout
**And** no logs or extraneous output appear on stdout (proves FR-TM-022 stdio transport)
**And** the round-trip p95 latency is ≤ 2 s with a warm REST connection and ≤ 100 TwinClasses (NFR-TM-001)

### Story 1.8: Testcontainers Integration Test Harness

As a **twins-mcp maintainer**,
I want **an integration test harness that exercises the running app against a real twins backend in Docker**,
So that **I can catch integration regressions (auth flow, header injection, response shape, pagination) before they reach a release**.

**Acceptance Criteria:**

**Given** Docker is available on the CI runner
**When** the developer runs `./gradlew integrationTest`
**Then** Testcontainers starts a twins Docker image
**And** the test waits for twins to be healthy before running assertions
**And** the harness tears down the container cleanly on test exit (success or failure)

**Given** the harness is configured
**When** `EndToEndIT` runs
**Then** it bootstraps the application with test env vars (TWINS_BASE_URL pointing to container, TWINS_DOMAIN_ID seeded, M2M credentials seeded)
**And** it invokes `list_classes` via the MCP stdio interface
**And** it asserts the response contains expected TwinClasses seeded into the test domain

**Given** `*IT.java` files exist
**When** `./gradlew build` is run
**Then** integration tests are NOT triggered by default (kept separate from unit tests)
**And** only `integrationTest` (or `build -PintegrationTests`) runs them

**Given** the test fixtures under `src/test/resources/fixtures/`
**When** reviewed
**Then** fixture JSON files exist for `twin_class_search_response.json` and related endpoints
**And** fixtures are used by unit tests in `client/endpoint/`, `tool/catalog/`, etc. (mocked RestClient)

**Given** integration tests fail
**When** the build reports
**Then** failure output includes the sanitised stderr log of the application under test
**And** the failure never includes the M2M secret or any token value
