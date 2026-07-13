---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
lastStep: 8
status: 'complete'
completedAt: '2026-06-26'
inputDocuments:
  - '_bmad-output/planning-artifacts/prds/prd-twins-mcp-2026-06-23/prd.md'
  - '_bmad-output/planning-artifacts/prds/prd-twins-mcp-2026-06-23/.decision-log.md'
  - '_bmad-output/brainstorming/brainstorming-session-2026-06-15-1628.md'
workflowType: 'architecture'
project_name: 'twins-mcp'
user_name: 'Nikita'
date: '2026-06-24'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**

20 FRs grouped into 6 categories. Architecturally the load-bearing ones are:

- **Domain Catalog tools (FR-TM-001…004).** Four read-only tools over the configured twins domain. `describe_class` is the canonical composite tool (D10, D29): one MCP tool internally issues two REST calls — class metadata via view-by-id + fields via `twin_class_fields/search/v2` with `showMode`. This composition pattern propagates to every other tool: MCP tool boundary ≠ REST endpoint boundary.
- **Terminology tools (FR-TM-010…012).** Glossary lives *inside* the domain as Twins of TwinClass key `TWINS_GLOSSARY` (D30). Tools call the standard twin search endpoint. Implication: tool surface must tolerate operator-divergent class schemas — no field name can be hard-coded beyond Twin name + serialized field map.
- **Connection & Auth (FR-TM-020…022).** Single MCP session = single domain = single M2M service account. M2M has no token scopes (D12); the read-only guarantee is operational (provisioning-time grant choice), not cryptographic. v1 = stdio only; v2 must slot in Streamable HTTP + OAuth 2.1 + PKCE + DCR + CIMD without breaking the tool layer (D7, D8, D19).
- **Output hygiene (FR-TM-040…042).** Hybrid response (markdown + structured JSON `structuredContent`), opaque pagination cursor, secrets sanitization everywhere. Response-size cap matters: some MCP clients silently truncate oversize results (D13).
- **Distribution (FR-TM-050…053).** OSS artifact under twins' license. Smithery listing, `claude mcp add` snippet, JSON config, GitHub releases with SHA-256 checksums. Architecture must not block any of these.
- **Module integration (FR-TM-060…062).** Lives as a Gradle submodule of twins (Option B, D9, D18). Reuses twins auth client, M2M flow, DTO classes, `@ProtectedBy` enforcement, `ApiUser` thread-local, `findEntitySafe()` / `isEntityReadDenied()` gate, OpenAPI config. Critical: **no parallel DTO layer** — DTOs are imported from twins core.

**Non-Functional Requirements:**

The NFRs that drive architecture (the rest are operational):

- **NFR-TM-001 Performance.** `list_classes` p95 ≤ 2 s; `describe_class*` p95 ≤ 3 s; glossary p95 ≤ 500 ms. Composite tools (two REST calls per MCP call) must hit these budgets — strict serial call patterns need scrutiny.
- **NFR-TM-003 / NFR-TM-004 (Security).** Secrets never leak; `DomainId` cannot be overridden by tool arguments. Both mandate architectural enforcement, not conventions.
- **NFR-TM-005 Observability.** Structured JSON logs to stderr (never stdout — that is the protocol channel). Local metrics, no remote telemetry in v1.
- **NFR-TM-006 Compatibility.** Java 21 + `--enable-preview`, Spring Boot 3.5+, MCP spec 2025-06-18. The `--enable-preview` flag is a hard constraint because the module lives inside the twins JVM.
- **NFR-TM-008 OSS ergonomics.** Clone-to-first-`list_classes` ≤ 10 min for a new user with Java 21. Architecture must keep the install path minimal.

**Scale & Complexity:**

- **Primary domain:** Backend — Spring Boot MCP server module. No UI, no real-time, no mobile. Single-process stdio binary.
- **Complexity level:** **Low-to-medium.** Small tool surface (7 tools), read-only, single-tenant-per-session, single-JVM. The complexity is not in breadth — it is in *correctness of REST composition* and *OSS ergonomics*, not in scale.
- **Estimated architectural components:** ~5 — Module skeleton; MCP server bootstrap; Tool registry + per-tool composition layer; REST client + auth + domain-scoping middleware; Output pipeline (markdown builder, pagination, sanitizer, logger).
- **Real-time features:** None.
- **Multi-tenancy:** Domain-scoped via `DomainId` header (one domain per session). No cross-domain browsing in v1 (PRD §2.2).
- **Regulatory compliance:** None explicit. Secrets hygiene and domain isolation are mandatory but not regulatory.

### Technical Constraints & Dependencies

- **Java 21 + `--enable-preview`** — pinned to twins runtime.
- **Spring Boot 3.5+** — module inherits from twins parent.
- **`spring-ai-mcp-server-spring-boot-starter`** — MCP protocol handling (D9, D18, FR-TM-061). Version pinned at architecture time.
- **MCP spec 2025-06-18** — stdio transport in v1, Streamable HTTP + OAuth 2.1 reserved for v2 (FR-TM-022, D19).
- **twins REST API v1 / v2** — per-endpoint version pinning; deprecation tracked (NFR-TM-007, D26, D28).
- **twins source tree** at `D:\work\esas\sources\java\twins` (D1) — sibling repo. CI access mode (submodule pin / sibling path / vendored allow-list) is the substance of OQ-PRD-4.
- **Smithery** — distribution registry (D14, FR-TM-050).
- **License parity** with twins.

### Cross-Cutting Concerns Identified

These touch every tool and must be designed once, applied uniformly:

1. **Authentication & session** — M2M token lifecycle (acquire, cache, refresh-on-401, never log). One service account per session.
2. **Domain isolation** — every outbound REST call carries `DomainId` derived from env var, never from tool args (NFR-TM-004).
3. **Permission gate** — every endpoint touched by a tool is `@ProtectedBy`-checked by twins itself; the module inherits this for free (FR-TM-062). Provisioning guide owns the read-only grant set.
4. **Output pipeline** — every tool response goes through: DTO → markdown summary builder → `structuredContent` JSON → size cap + pagination → sanitizer. One pipeline, not per-tool.
5. **Error envelope** — twins errors (403, 404, 5xx) mapped to a single structured MCP error shape with twins error code + redacted message; never a stack trace.
6. **Structured logging to stderr** — JSON, secrets redacted, never to stdout. One logger config, not per-tool.
7. **No-deprecated-endpoints guardrail (D26)** — CI gate that scans the module's declared endpoint inventory against twins' `@Deprecated` markers. Release gate re-runs the same check. Allowed-endpoint inventory is part of the repo.
8. **Prefer-search-endpoints guardrail (D28)** — view-by-id only when no search equivalent exists. Verified at PR review against the inventory.
9. **Read-only surface (NFR / Non-Goal §5)** — no mutating tool ever registered; enforced by tool registry allowlist at startup.
10. **Composite-tool discipline (D10)** — tool boundary ≠ endpoint boundary; tools compose REST calls. This affects how tools are structured, tested, and documented.

## Starter Template Evaluation

### Primary Technology Domain

**Backend — Spring Boot 4 MCP server.** Java 25 LTS + Spring Boot 4.1, single-process stdio binary. Standalone build, not a twins submodule.

### Starter Options Considered

#### Option A — Track twins (Spring AI 1.0.9 + Boot 3.5.15)
- Rejected. Boot 3.5 reaches OSS EOL on 2026-06-30 (4 days from architecture date).
- Keeps D9/D18 ("module inside twins") intact but ships on a dead platform.

#### Option B — Spring AI 2.0 + Boot 4.1, standalone build, depends on `twins-core-dto`
- **Selected.** Standalone Gradle build/repo on Boot 4.1.0 + Java 25 LTS.
- Depends on published `com.alcosi.twins:twins-core-dto:1.4.191` (Maven Central, Apache 2.0) for DTO classes — lightweight, no Spring Boot coupling.
- Talks to twins via HTTP loopback to `TWINS_BASE_URL`.
- **Overrides D9/D18/FR-TM-060** (no longer a Gradle submodule inside twins).

#### Option C — Direct MCP Java SDK, no Spring AI
- Rejected. Defies D9/D18's "via starter" sub-decision; loses auto-config; manual transport/tool-registration wiring.

### Selected Starter: Option B

**Coordinates:**

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
// no --enable-preview by default; opt-in per feature if needed

dependencies {
    // DTO classes — shared with twins via published Maven artifact
    implementation 'com.alcosi.twins:twins-core-dto:1.4.191'

    // MCP server, stdio transport auto-configured
    implementation 'org.springframework.ai:spring-ai-starter-mcp-server'

    // RestClient carrier without the spring-boot-starter-web (Tomcat + MVC) stack.
    // twins-mcp is a stdio process (web-application-type: none); spring-web alone
    // gives us RestClient. Code-review Decision-1 (2026-07-08).
    implementation 'org.springframework:spring-web'
}
```

Managed via `org.springframework.ai:spring-ai-bom:2.0.0` (imports Boot 4.1.0 dependency BOM transitively). **Note (2026-07-08 code review):** the `io.spring.dependency-management` plugin originally specified at §109 was dropped — version `1.1.8` is not published for Boot 4.x, and Gradle's native `platform()` import is the Boot 4-recommended substitute. The Spring Boot Gradle plugin continues to manage its own starter versions; the Spring AI BOM is imported via `implementation platform(libs.spring.ai.bom)`. OQ-ARCH-3 is closed as "verified via substitution" (no functional gap for current dependencies).

### Rationale for Selection

1. **Boot 3.5 EOL is 4 days away.** Shipping a v1 OSS product on an EOL platform is unacceptable; users get security warnings from day one.
2. **User direction (2026-06-26):** go directly to Boot 4.
3. **DTO library already published.** `com.alcosi.twins:twins-core-dto:1.4.191` on Maven Central means DTO-sharing does not require sharing a Spring Boot runtime with twins. This neutralises the binary-incompatibility objection to Boot 4.
4. **Standalone build is the right shape anyway.** OSS distribution (FR-TM-050…053) is cleaner from a separate repo: own release cadence, own Smithery listing, own issue tracker, no entanglement with twins' release train. Module-inside-twins (original D9/D18) optimised for in-process reuse; with DTOs published, that optimisation is no longer worth the Boot-version coupling.
5. **Migration path stays open.** If twins core later migrates to Boot 4 and wants to absorb twins-mcp back as a submodule, the standalone build can be collapsed without touching tool code — only `build.gradle` changes.
6. **Java 25 LTS over Java 21.** Once `--enable-preview` is no longer needed (most preview features stable in 25), Java 25 LTS gives the longest support window (Oracle LTS until 2030+, Azul/Amazon similar). Decouples from twins' Java 21 pin (D32 precedent).

### Architectural Decisions Provided by Starter

- **Language & Runtime:** Java 25 LTS; Gradle 9.6+; Spring Boot 4.1.0.
- **MCP transport:** stdio auto-configured by `spring-ai-starter-mcp-server` (FR-TM-022). Streamable HTTP is opt-in via the same starter (reserved for v2).
- **Tool registration:** Spring beans annotated per the starter's conventions; tool-registry allowlist (Cross-cutting concern #9, Read-only surface) layered on top.
- **Build tooling:** Boot fat JAR via `bootJar` for FR-TM-053 GitHub release artifacts.
- **Testing:** JUnit 5 + Spring Boot Test (Boot 4 ships both).
- **Logging:** Logback or Log4j2 (chosen at Step 5). Secrets masking via a sanitiser filter — re-implemented in twins-mcp (twins' `log4j2-masking-factory` is not on classpath anymore since we are no longer inside twins build).
- **OpenAPI consumption:** HTTP GET to `TWINS_BASE_URL/api-docs` (springdoc already exposed by twins). Optional in v1 per FR-TM-062.

### Version Pinning

| Component | Version | Status | Notes |
|---|---|---|---|
| Spring Boot | **4.1.0** | GA (2026-06-10) | Pulled in by Spring AI 2.0.0 BOM (Issue #6465) |
| Spring AI | **2.0.0** | GA (2026-05-28) | Requires Boot 4.0+; designed for Boot 4.0.x/4.1.x |
| spring-ai-starter-mcp-server | via BOM | Current artifact name | Legacy `spring-ai-mcp-server-spring-boot-starter` abandoned at 1.0.0-M6 |
| MCP Java SDK | via BOM (transitively) | Tracks Spring AI 2.0.x | |
| MCP spec | 2025-06-18 | Current | Declared in server metadata (NFR-TM-006) |
| twins-core-dto | **1.4.191** | Maven Central (2026-03-13) | Apache 2.0; DTO-only |
| **Java** | **25 LTS** | **GA (2025-09-16)** | **No `--enable-preview`; LTS until 2030+** |

### PRD / Decision-Log Deltas (for follow-up by PM)

The Option B selection modifies three earlier decisions. These are flagged for the PM (John) to fold back into the PRD body after architecture completes; the architecture document proceeds under the new direction now.

- **D9 — overridden.** "Module inside twins" → standalone Gradle build/repo. Rationale: Boot 4 + published DTO artifact removes the original coupling argument.
- **D18 — overridden.** "Spring Boot module inside twins" → standalone Spring Boot 4.1 app on its own release cadence.
- **FR-TM-060 — overridden.** "Gradle submodule at `modules/twins-mcp/`" → top-level Gradle project (separate repo).
- **FR-TM-062 — partially preserved.** DTO layer IS reused via `com.alcosi.twins:twins-core-dto:1.4.191` (no parallel DTO layer — the original intent holds). Auth client, REST client, OpenAPI config, `ApiUser` thread-local, `findEntitySafe()`/`isEntityReadDenied()` gate are NOT reused — they are reimplemented in twins-mcp. The `@ProtectedBy` enforcement continues to apply because twins still executes the REST calls server-side.
- **NFR-TM-006 — modified.** Java 21 + `--enable-preview` → **Java 25 LTS, no `--enable-preview` by default**. Original "matches twins" rationale removed by D32.
- **PRD §Integration.** The "in-process, same JVM — preferred — or HTTP loopback" option is reduced to **HTTP loopback only**.

### Proposed Decision-Log Additions (D32–D34)

> **D32.** *Standalone build on Boot 4.* twins-mcp ships as a standalone Gradle build/repo on Spring Boot 4.1.0 + Spring AI 2.0.0. Overrides D9/D18/FR-TM-060. Rationale: Boot 3.5 OSS EOL 2026-06-30 (4 days away); user direction 2026-06-26 to target Boot 4 directly; `com.alcosi.twins:twins-core-dto` published on Maven Central makes DTO-sharing decoupled from runtime.

> **D33.** *DTO reuse via Maven Central artifact.* twins-mcp depends on `com.alcosi.twins:twins-core-dto:1.4.191` (Apache 2.0, Maven Central) for DTO classes. Preserves the FR-TM-062 "no parallel DTO layer" intent; loses reuse of twins' auth client, REST client, OpenAPI config, `ApiUser` thread-local, and `findEntitySafe()`/`isEntityReadDenied()` gate (those are reimplemented in twins-mcp or apply server-side via twins REST).

> **D34.** *Version pins for v1.* Spring Boot 4.1.0, Spring AI 2.0.0, MCP spec 2025-06-18, `twins-core-dto` 1.4.191, **Java 25 LTS (no `--enable-preview`)**. All pinned at architecture time per NFR-TM-006 (modified).

### Open Questions Surfaced (for Step 4)

- **OQ-ARCH-1.** Does `twins-core-dto:1.4.191` compile cleanly under Java 25? Need to verify no usage of APIs removed in Java 23/24/25.
- **OQ-ARCH-2.** CI strategy for the no-deprecated-endpoints guardrail (D26) now that twins source is no longer co-located with twins-mcp build. Options: submodule pin, scheduled clone-and-scan, vendored allow-list. Resolved at Step 4.
- **OQ-ARCH-3.** ~~`io.spring.dependency-management` plugin version compatible with Boot 4.1 — confirmed `1.1.8` works with Boot 4.x baseline (verify in implementation story).~~ **RESOLVED (2026-07-08 code review):** plugin version `1.1.8` is not published for Boot 4.x. Substituted with Gradle's native `platform()` import (`implementation platform(libs.spring.ai.bom)`) plus the Spring Boot Gradle plugin's built-in BOM management. No functional gap for current dependencies. Closed as "verified via substitution".

**Note:** Project initialization (new Gradle build, Boot 4.1 plugin, BOM import, DTO dep, Java 25 toolchain) is the first implementation story. Distribution artifact (FR-TM-053) is the Boot fat JAR from `bootJar`.

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- ARCH-4 M2M auth client; ARCH-5 env var names; ARCH-7 DomainId injection; ARCH-10 HTTP client; ARCH-11 composite-tool pattern; ARCH-12 error envelope; ARCH-20 no-deprecated CI gate.

**Important Decisions (Shape Architecture):**
- ARCH-2 in-memory caches; ARCH-8 secrets sanitiser; ARCH-9 read-only allowlist; ARCH-13 cursor; ARCH-14 hybrid response builder; ARCH-15 resilience; ARCH-16 response cap; ARCH-17 showMode=MapperContext; ARCH-21 logging; ARCH-22 metrics.

**Deferred Decisions (Post-MVP):**
- ARCH-3 TwinClass metadata cache (off in v1); multi-domain browsing (PRD §6.2 v2); Streamable HTTP transport (v2).

### Data Architecture

**ARCH-1 — No database.** twins-mcp is a stateless stdio process. All data lives in twins. *Rationale:* NFR-TM-002 reliability; PRD §5 Non-Goals.

**ARCH-2 — In-memory caches only** (Caffeine or `ConcurrentHashMap` with TTL). Caches: M2M token (TTL = `expires_in` − 60 s); optional TwinClass metadata (TTL = 60 s, off by default per ARCH-3). Process dies → cache dies; acceptable.

**ARCH-3 — TwinClass metadata cache in v1: off.** Keep v1 simple. Add only if NFR-TM-001 latency targets are missed in load test.

### Authentication & Security

**ARCH-4 — M2M client in twins-mcp.** Custom Spring component `TwinsM2MClient` POSTs `/auth/m2m/token/v1`, extracts the auth token from the `authData` map (`AuthM2MTokenRsDTOv1.authData: Map<String, String>`). The token is then sent on every subsequent REST call via the **`AuthToken` header** (NOT standard `Authorization: Bearer …`) per twins convention — see `org.twins.core.service.HttpRequestService.HEADER_AUTH_TOKEN`. Endpoint is **not** deprecated (only `/auth/m2m/login/v1` is — *do not use*). *Rationale:* D33 — auth client is not reused from twins.

**ARCH-5 — Env vars (corrects PRD FR-TM-020 [A1]):**

| Env var | Required | Maps to DTO field |
|---|---|---|
| `TWINS_BASE_URL` | yes | (root URL for RestClient) |
| `TWINS_DOMAIN_ID` | yes | `DomainId` header |
| `TWINS_M2M_CLIENT_ID` | yes | `AuthM2MLoginRqDTOv1.clientId` |
| `TWINS_M2M_CLIENT_SECRET` | yes | `AuthM2MLoginRqDTOv1.clientSecret` |
| `TWINS_M2M_PUBLIC_KEY_ID` | no | `AuthM2MLoginRqDTOv1.publicKeyId` |

*PM action required:* update PRD FR-TM-020 to remove `TWINS_M2M_LOGIN` / `TWINS_M2M_SECRET` (incorrect) and replace with the names above. Removes [A1] assumption.

**ARCH-6 — Token lifecycle.** Singleton token-holder; refresh 60 s before `expires_in`; single retry on HTTP 401; structured error to MCP client on repeat 401. Token cached in memory only (NFR-TM-003).

**ARCH-7 — `DomainId` + `AuthToken` injection at HTTP layer.** `ClientHttpRequestInterceptor` (renamed `TwinsHeadersInterceptor`) sets both twins-mandated headers on every outbound call:
- `DomainId` — from `TWINS_DOMAIN_ID` env var; tools cannot override (NFR-TM-004).
- `AuthToken` — from in-memory `TokenHolder` (acquired via ARCH-4); tools cannot override.

Both header names are twins-defined constants in `org.twins.core.service.HttpRequestService` (`HEADER_DOMAIN_ID = "DomainId"`, `HEADER_AUTH_TOKEN = "AuthToken"`). The interceptor is the single point of header attachment; no other component may set them. *Rationale:* NFR-TM-003 + NFR-TM-004 — architectural enforcement, not convention.

**ARCH-8 — Secrets sanitiser.** Logback filter with pattern-based redaction: redacts token-shaped strings (JWT/Bearer patterns, opaque-token base64), base64 blobs ≥32 chars, and the literal values of `TWINS_M2M_CLIENT_SECRET` and the in-memory `AuthToken` value. Applied at the logger boundary so no app code bypasses it. (Note: "Bearer" here is a sanitiser pattern name only — twins transport uses the `AuthToken` header, not standard `Authorization: Bearer`.)

**ARCH-9 — Read-only enforcement at startup.** Tool registry checks every registered `TwinsMcpTool` bean against an allowlist (`ToolAllowlist`). Non-allowlisted bean → startup failure (fast-fail). Prevents accidental or malicious registration of a mutating tool.

### API & Communication

**ARCH-10 — HTTP client = Spring Boot 4 `RestClient` (synchronous).** WebClient rejected — no reactive code anywhere in v1; stdio process gains nothing from non-blocking I/O. *Rationale:* simplicity; Boot 4 native; matches Java 25 idiom. Future v2 Streamable HTTP transport can introduce WebClient at the MCP-transport layer without rewriting the twins-REST client.

**ARCH-11 — Composite-tool pattern.** Every MCP tool is a Spring bean implementing `TwinsMcpTool` interface with a single `call(ToolArgs): ToolResponse` method. Tool freely composes N REST calls (e.g., `describe_class` issues 2: class metadata view-by-id + fields search). *Rationale:* D10 — tool boundary ≠ endpoint boundary.

**ARCH-12 — Error envelope.** twins errors mapped to a single MCP error shape:

```json
{
  "code": "TWINS_ERROR" | "TWINS_UNAVAILABLE" | "PERMISSION_DENIED" | "NOT_FOUND",
  "message": "<redacted human-readable>",
  "twinsErrorCode": "<original twins error code if available>",
  "domainId": "<TWINS_DOMAIN_ID>"
}
```

Stack traces never escape the process. Mapping: HTTP 403 → `PERMISSION_DENIED`; 404 → `NOT_FOUND`; 5xx → `TWINS_UNAVAILABLE`; other 4xx → `TWINS_ERROR`.

**ARCH-13 — Pagination cursor** = opaque base64-encoded JSON `{page, pageSize, filterHash}`. `filterHash` is a SHA-256 of the request filter; tool refuses to advance cursor if incoming filter hash ≠ cursor's hash (prevents inconsistent pages when agent changes filter mid-pagination).

**ARCH-14 — Hybrid response builder.** Single `ToolResponseBuilder`: takes DTO(s) → renders markdown summary (≤30 % of structured JSON size) + structuredContent JSON. One builder, not per-tool.

**ARCH-15 — Resilience.** Resilience4j retry: 3 attempts, exponential backoff 100 ms / 200 ms / 400 ms, on HTTP 5xx and `IOException`. No retry on 4xx. Per-call timeout 30 s.

**ARCH-16 — Response size cap = 32 KiB** (OQ-PRD-2 resolved). Over-cap responses auto-paginate.

**ARCH-17 — showMode = MapperContext query params** (OQ-PRD-5 resolved). For `describe_class` fields: pass `showTwinClassFieldMode=DETAILED` (exact param name verified at story time by inspecting `TwinClassFieldRestDTOMapper`'s `@MapperContextBinding`). Mechanism documented in `twins/docs/rest_api.md` §Show Modes — pointer-propagation pattern.

### Infrastructure & Deployment

**ARCH-18 — Build.** Boot 4.1.0 fat JAR via `bootJar` (from starter).

**ARCH-19 — Distribution.** GitHub release with: fat JAR + SHA-256 checksum + Docker image via `bootBuildImage` (Boot 4 native, Paketo base). No bespoke Dockerfile in v1.

**ARCH-20 — CI = GitHub Actions.** Build + test on push; release on tag. **No-deprecated-endpoints gate (D26):** `twins` repo added as git-submodule under `vendor/twins/`, pinned at the release tag twins-mcp is being built against. CI greps `@Deprecated` annotations on every endpoint listed in the allowed-endpoint inventory; PR fails if a forbidden call is introduced. Allowed-endpoint inventory committed to `twins-mcp` repo (file: `docs/allowed-endpoints.md`). Release gate re-runs the same check. Resolves OQ-PRD-4 / OQ-ARCH-2.

**ARCH-21 — Logging.** Logback (Boot 4 default) + JSON encoder (Logstash-style or built-in) + secrets sanitiser filter (ARCH-8). Output **to stderr only** — stdout is the MCP protocol channel (FR-TM-022).

**ARCH-22 — Observability.** Micrometer counters per tool + latency histogram. In stdio mode, `/actuator` HTTP endpoints are disabled; metrics are emitted as a single log line on graceful shutdown (SIGTERM) and discarded on hard exit.

**ARCH-23 — Module layout (top-level).** `app/` (bootstrap, `Application.java`, application.yml), `core/` (tools, RestClient config, M2M client, auth, response builder), `infra/` (CI workflows, release scripts, Smithery manifest, `vendor/twins/` submodule pointer). Final package layout decided at Step 6.

### Open Question Resolutions (PRD §8)

| OQ | Resolution | Decision |
|---|---|---|
| **OQ-PRD-1** | ✅ Env var names corrected to `TWINS_M2M_CLIENT_ID` / `TWINS_M2M_CLIENT_SECRET` (+ optional `TWINS_M2M_PUBLIC_KEY_ID`). **PM action:** update PRD FR-TM-020 to remove `[A1]` assumption. | ARCH-5 |
| **OQ-PRD-2** | ✅ Response size cap = 32 KiB. | ARCH-16 |
| **OQ-PRD-3** | ✅ Independent release cadence — twins-mcp is standalone build per D32. | D32 |
| **OQ-PRD-4** | ✅ Git-submodule of twins under `vendor/twins/`, pinned at release tag, CI greps `@Deprecated`. | ARCH-20 |
| **OQ-PRD-5** | ✅ Twins uses `MapperContext` HTTP query params (not literal `showMode`). For fields: `showTwinClassFieldMode=DETAILED`. Exact param name verified at story time. | ARCH-17 |
| **OQ-PRD-6** | ✅ **Recommended `TWINS_GLOSSARY` schema** (documented in provisioning guide, not fixed in code): Twin name = term; fields `section` (String), `definition` (Text), `jpa_path` (String, optional), `table_name` (String, optional), `notes` (Text, optional). | ARCH-24 |
| **OQ-PRD-7** | ✅ Group by distinct values of `section` field on glossary Twins; aggregate client-side. No separate "sections" TwinClass. | ARCH-24 |

**ARCH-24 — Glossary provisioning.** Provisioning guide documents recommended `TWINS_GLOSSARY` schema (OQ-PRD-6) and section grouping mechanism (OQ-PRD-7). Tools tolerate operator-divergent schemas — only Twin name + serialized field map are hard-required.

### Decision Impact Analysis

**Implementation Sequence (for Step 6 / Stories):**

1. Project init (Gradle, Boot 4.1, BOM, Java 25 toolchain, twins submodule) — ARCH-D32..D34
2. `app/` bootstrap, application.yml, env var parsing, startup validation — ARCH-5
3. `TwinsM2MClient` + token cache + RestClient bean + DomainId interceptor — ARCH-4, ARCH-6, ARCH-7, ARCH-10
4. Secrets sanitiser filter wired into Logback — ARCH-8
5. Tool registry + `ToolAllowlist` startup check — ARCH-9, ARCH-11
6. `ToolResponseBuilder` (markdown + structuredContent) + cursor codec + size cap — ARCH-13, ARCH-14, ARCH-16
7. Error envelope mapper + Resilience4j config — ARCH-12, ARCH-15
8. Per-tool implementations (FR-TM-001…004, FR-TM-010…012) — uses everything above
9. Distribution: `bootBuildImage`, GitHub release workflow, Smithery manifest — ARCH-19, ARCH-20
10. CI: no-deprecated-endpoints gate — ARCH-20

**Cross-Component Dependencies:**

- `TwinsM2MClient` (ARCH-4) is consumed by every tool via the RestClient bean.
- `DomainId` interceptor (ARCH-7) sits between RestClient and the network; cannot be bypassed.
- `ToolResponseBuilder` (ARCH-14) is consumed by every tool's output stage.
- Error envelope (ARCH-12) is the single egress for non-success paths; tools throw, framework catches.
- `ToolAllowlist` (ARCH-9) is the runtime embodiment of the read-only Non-Goal (PRD §5).
- `vendor/twins/` submodule (ARCH-20) is read at CI time only, never at runtime.

## Implementation Patterns & Consistency Rules

### Pattern Categories Defined

Many generic conflict points (DB naming, component routes, event payloads) are non-applicable to twins-mcp: stateless stdio process, no database, no UI, no event bus. The patterns below address only the conflict points where AI agents could actually diverge.

**Critical conflict points identified:** 6 — naming, package layout, JSON casing, logging shape, error throwing, tool registration.

### Naming Patterns

**MCP tool names:**
- Format: `snake_case`, verb-first (`list_classes`, `describe_class`, `get_glossary_term`).
- IDs stable across versions: never rename a shipped tool. Deprecation per PRD §Versioning (one minor release of dual-support).
- New tool ⇒ update `ToolAllowlist` (ARCH-9) in same PR.

**Java packages:**
- Root: `org.twins.mcp`.
- Sub-packages: `app` (bootstrap), `client` (RestClient + M2M + interceptor), `tool` (tool implementations + `ToolAllowlist`), `response` (builder + cursor + sanitiser hook), `config` (Bean configs, `application.yml` mapping), `error` (envelope + mapper).
- Inside `tool`: one sub-package per capability — `tool.catalog`, `tool.glossary`. One file per tool.

**Java classes:**
- Tools: `<VerbNoun>Tool.java` — `ListClassesTool.java`, `DescribeClassTool.java`, `GetGlossaryTermTool.java`.
- Records: `<Concept>DTO` / `<Concept>Args` / `<Concept>Result` — `ListClassesArgs`, `DescribeClassResult`.
- Config props: `<Area>Properties` — `TwinsConnectionProperties`, `M2mCredentialsProperties`.

**Config keys:**
- `application.yml`: `kebab-case` (`twins.base-url`, `twins.m-2-m.client-id`).
- `@ConfigurationProperties` fields: camelCase (Spring binds kebab-case YAML to camelCase fields).

### Structure Patterns

**Test layout:**
- Co-located with main: `src/test/java/...` mirrors `src/main/java/...`.
- One test class per class: `ListClassesToolTest.java`.
- Integration tests (real twins via Testcontainers against `TWINS_BASE_URL=localhost`): `src/test/java/.../integration/`, suffixed `IT.java`. Skipped in default `test` task; run via `integrationTest` task.

**Tool file organisation:**
- One tool per file. Tool class + its `Args` record + its `Result` record all live in the same file (records as nested types or sibling records in same package).
- Rationale: parallel agent work — two agents adding two different tools touch two different files, zero merge conflicts.

**Shared utilities:**
- `response/` package owns `ToolResponseBuilder`, `CursorCodec`, `MarkdownSummariser`. Tools depend on these, never re-implement.
- `client/` owns RestClient bean + interceptors. Tools depend on injected `TwinsRestClient`, never construct their own.

### Format Patterns

**structuredContent JSON casing:**
- **snake_case** for tool-exposed JSON keys (MCP convention; agent-facing).
- twins DTO fields (from `twins-core-dto` JAR) are mixed camelCase per Java — these are kept **as-is** in nested `twins` objects inside structuredContent (don't transliterate; pass-through verbatim).
- Top-level MCP envelope keys (`next_cursor`, `twins_error_code`, `domain_id`): snake_case.

**Null handling:**
- Omit `null` fields from structuredContent JSON (Jackson `@JsonInclude(NON_NULL)` at class level). Cleaner agent context than explicit `null`.
- Empty collections: render as `[]`, never `null`.

**Date/time:**
- ISO-8601 UTC with `Z` suffix (`2026-06-26T14:32:15Z`) wherever timestamps appear in logs or tool output.
- `java.time.Instant` everywhere; never `Date` or epoch millis.

**Markdown summary:**
- CommonMark, no GFM extensions.
- Lists of entities: GitHub-flavored markdown tables (`| id | name | ... |`).
- Single entity: bold-key block (`**Field:** value`).
- Always include the literal tool name and a one-line hint about pagination if list is partial.

### Communication Patterns

**No event system.** twins-mcp has no pub/sub, no async messaging, no scheduled jobs beyond in-process token refresh. Don't add one speculatively.

**Logging:**
- Structured JSON, one event per line, to stderr only.
- Schema: `{"ts": "<ISO8601>", "level": "ERROR|WARN|INFO|DEBUG|TRACE", "tool": "<tool name or null>", "msg": "...", "extra": {...}}`.
- Levels:
  - `ERROR` — operation failed; tool returned error envelope.
  - `WARN` — retry happened; fallback path taken; non-fatal anomaly.
  - `INFO` — tool invocation start/finish, lifecycle events (startup, shutdown).
  - `DEBUG` — REST request/response (URL, status, latency — never body or headers).
  - `TRACE` — off by default; verbose DTO dump for debugging.
- Every line passes through secrets sanitiser (ARCH-8) — no exceptions, no `System.out.println`, no `printStackTrace`.

### Process Patterns

**Tool args validation:**
- Every tool's `Args` is a Java `record` with JSR-380 (`jakarta.validation`) annotations: `@NotBlank`, `@Size(max=...)`, `@Pattern(...)`, `@UUID`.
- Framework validates `Args` before `call()` is invoked; invalid args → MCP error envelope (`code=VALIDATION_ERROR`), tool never sees them.

**Error throwing:**
- Tools throw domain-checked exceptions: `TwinsPermissionDenied`, `TwinsNotFound`, `TwinsUnavailable`, `ToolInputInvalid`.
- Tools **never** throw raw `RuntimeException` or `IOException`. The framework's error mapper (ARCH-12) catches and converts.
- Tools never catch-and-swallow; let it propagate.

**Retry:**
- Configured once via Resilience4j at RestClient level (ARCH-15).
- Individual tools cannot override retry policy. If a tool needs different behaviour (e.g., no retry on idempotency grounds), raise an ADR.

**Validation at boundaries:**
- Process start: env vars validated, fail-fast with clear message if missing/malformed.
- Tool args: JSR-380 at framework layer.
- Twins response: trust the DTO types from `twins-core-dto` JAR — no redundant null-checks on fields the DTO guarantees non-null.

### Enforcement Guidelines

**All AI Agents MUST:**
1. Register any new tool in `ToolAllowlist` (ARCH-9) in the same PR — a tool that exists but isn't allowlisted will fail startup.
2. Run tool args through JSR-380; never hand-validate.
3. Throw domain exceptions, never raw `RuntimeException`.
4. Route all logs through the configured Logback filter; no `System.err` directly.
5. Keep structuredContent top-level keys snake_case; pass twins DTO fields verbatim.
6. Add an entry to `docs/allowed-endpoints.md` for any new twins endpoint used; CI gate (ARCH-20) will fail otherwise.
7. Pin any new third-party dependency in `gradle/libs.versions.toml` (version catalog); no inline version strings.

**Pattern Enforcement:**
- Static checks via Spotless + Checkstyle in CI; PR fails on style violation.
- Architecture review on any PR touching `client/`, `response/`, or `error/` packages (cross-cutting — bigger blast radius).
- New patterns or exceptions to existing patterns ⇒ ADR commit under `docs/adr/NNNN-short-name.md`.

### Pattern Examples

**Good — a tool implementation:**
```java
@Component
public class GetGlossaryTermTool implements TwinsMcpTool {
    private final TwinsRestClient restClient;
    private final ToolResponseBuilder responseBuilder;

    @Override
    public ToolKey key() { return ToolKey.GET_GLOSSARY_TERM; }  // registered in ToolAllowlist

    @Override
    public ToolResponse call(@Valid GetGlossaryTermArgs args) {
        var rs = restClient.searchTwin(SearchRequest.builder()
            .twinClassIdList(List.of(glossaryClassId()))
            .twinNameLikeList(List.of(args.term().toLowerCase()))
            .build());
        return responseBuilder.hybrid(rs)
            .markdown(glossaryMarkdown(rs))
            .build();
    }

    public record GetGlossaryTermArgs(
        @NotBlank @Size(max = 200) String term,
        @Min(1) @Max(100) Integer pageSize  // optional, defaults at framework layer
    ) {}
}
```

**Anti-pattern — tool doing too much:**
```java
// DON'T: validate args by hand, catch IOException, log to stderr
public ToolResponse call(Map<String,Object> rawArgs) {
    var term = (String) rawArgs.get("term");
    if (term == null || term.isEmpty()) {  // use JSR-380
        System.err.println("missing term");  // use logger
        ...
    }
    try {
        var rs = httpClient.send(...);  // use injected TwinsRestClient
    } catch (IOException e) {  // let framework handle
        return errorResponse(e.getMessage());  // use error envelope
    }
}
```

## Project Structure & Boundaries

### Complete Project Directory Structure

```
twins-mcp/
├── build.gradle                         # root build (Boot 4.1, BOM, deps)
├── settings.gradle                      # rootProject.name = 'twins-mcp'
├── gradle.properties                    # version pins (group, docker, profile)
├── gradle/
│   └── libs.versions.toml               # version catalog (per Pattern Enforcement)
├── gradlew / gradlew.bat
├── README.md                            # FR-TM-051 install snippet
├── LICENSE                              # Apache 2.0 (twins parity)
├── CHANGELOG.md
├── .gitignore / .gitattributes / .editorconfig
├── claude.json                          # MCP install manifest for Claude (FR-TM-052)
├── smithery.json                        # FR-TM-050 Smithery listing
│
├── .github/
│   └── workflows/
│       ├── ci.yml                       # build+test on push
│       ├── release.yml                  # tag → GitHub release (FR-TM-053)
│       └── check-deprecated.yml         # no-deprecated gate (ARCH-20)
│
├── docs/
│   ├── allowed-endpoints.md             # twins endpoint inventory (ARCH-20)
│   ├── provisioning-guide.md            # TWINS_GLOSSARY schema (ARCH-24)
│   └── adr/                             # architecture decision records
│       └── 0001-template.md
│
├── vendor/
│   └── twins/                           # git submodule (ARCH-20)
│
├── scripts/
│   ├── check-deprecated-endpoints.sh    # CI helper for ARCH-20
│   └── release.sh
│
├── src/
│   ├── main/
│   │   ├── java/org/twins/mcp/
│   │   │   ├── app/
│   │   │   │   ├── Application.java                # @SpringBootApplication, main()
│   │   │   │   └── StartupEnvValidator.java        # fail-fast on missing env (ARCH-5)
│   │   │   ├── config/
│   │   │   │   ├── TwinsConnectionProperties.java # TWINS_BASE_URL, TWINS_DOMAIN_ID
│   │   │   │   ├── M2mCredentialsProperties.java   # TWINS_M2M_*
│   │   │   │   ├── RestClientConfig.java           # RestClient bean + TwinsHeadersInterceptor
│   │   │   │   ├── ResilienceConfig.java           # Resilience4j (ARCH-15)
│   │   │   │   ├── LoggingConfig.java              # Logback JSON layout hook
│   │   │   │   ├── McpServerConfig.java            # MCP server bean registration
│   │   │   │   └── MetricsConfig.java              # Micrometer (ARCH-22)
│   │   │   ├── client/
│   │   │   │   ├── TwinsRestClient.java            # RestClient facade
│   │   │   │   ├── TwinsM2MClient.java             # M2M auth (ARCH-4)
│   │   │   │   ├── TokenHolder.java                # token cache (ARCH-6)
│   │   │   │   ├── TwinsHeadersInterceptor.java    # DomainId + AuthToken injection (ARCH-7)
│   │   │   │   └── endpoint/                       # per-resource REST wrappers
│   │   │   │       ├── TwinClassSearchEndpoint.java
│   │   │   │       ├── TwinClassFieldSearchEndpoint.java
│   │   │   │       ├── LinkSearchEndpoint.java
│   │   │   │       ├── TwinStatusSearchEndpoint.java
│   │   │   │       └── TwinSearchEndpoint.java     # glossary Twins (FR-TM-010…012)
│   │   │   ├── tool/
│   │   │   │   ├── TwinsMcpTool.java               # interface
│   │   │   │   ├── ToolKey.java                    # enum (allowlist of tool names)
│   │   │   │   ├── ToolAllowlist.java              # startup enforcement (ARCH-9)
│   │   │   │   ├── ToolRegistry.java               # bean discovery + registration
│   │   │   │   ├── ToolArgsValidator.java          # JSR-380 hook
│   │   │   │   ├── catalog/
│   │   │   │   │   ├── ListClassesTool.java        # FR-TM-001
│   │   │   │   │   ├── DescribeClassTool.java      # FR-TM-002 (composite)
│   │   │   │   │   ├── DescribeClassRelationsTool.java # FR-TM-003
│   │   │   │   │   └── DescribeClassStatusesTool.java  # FR-TM-004
│   │   │   │   └── glossary/
│   │   │   │       ├── ListGlossarySectionsTool.java  # FR-TM-010
│   │   │   │       ├── GetGlossarySectionTool.java    # FR-TM-011
│   │   │   │       ├── GetGlossaryTermTool.java       # FR-TM-012
│   │   │   │       └── GlossaryClassResolver.java     # caches TWINS_GLOSSARY class id
│   │   │   ├── response/
│   │   │   │   ├── ToolResponse.java
│   │   │   │   ├── ToolResponseBuilder.java        # ARCH-14
│   │   │   │   ├── CursorCodec.java                # ARCH-13
│   │   │   │   ├── MarkdownSummariser.java
│   │   │   │   └── SizeCapEnforcer.java            # ARCH-16
│   │   │   ├── error/
│   │   │   │   ├── ToolException.java              # base
│   │   │   │   ├── TwinsPermissionDenied.java
│   │   │   │   ├── TwinsNotFound.java
│   │   │   │   ├── TwinsUnavailable.java
│   │   │   │   ├── ToolInputInvalid.java
│   │   │   │   └── ErrorEnvelopeMapper.java        # ARCH-12
│   │   │   ├── secrets/
│   │   │   │   └── SecretsSanitiser.java           # ARCH-8
│   │   │   └── metrics/
│   │   │       ├── ToolMetrics.java                # ARCH-22
│   │   │       └── MetricsEmitOnShutdown.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-stage.yml
│   │       ├── application-release.yml
│   │       └── logback-spring.xml                  # JSON encoder + sanitiser filter
│   └── test/
│       ├── java/org/twins/mcp/
│       │   ├── client/
│       │   │   ├── TwinsM2MClientTest.java
│       │   │   ├── TokenHolderTest.java
│       │   │   ├── DomainIdInterceptorTest.java
│       │   │   └── endpoint/*Test.java             # per endpoint
│       │   ├── tool/
│       │   │   ├── catalog/*Test.java              # per tool
│       │   │   └── glossary/*Test.java
│       │   ├── response/*Test.java
│       │   ├── error/*Test.java
│       │   ├── secrets/SecretsSanitiserTest.java
│       │   └── integration/                        # IT.java suffix; Testcontainers
│       │       ├── M2MAuthFlowIT.java
│       │       └── EndToEndIT.java
│       └── resources/
│           ├── application-test.yml
│           └── fixtures/                           # canned twins REST responses
│               ├── twin_class_search_response.json
│               ├── twin_class_field_search_response.json
│               ├── link_search_response.json
│               ├── twin_status_search_response.json
│               └── twin_search_glossary_response.json
```

### Architectural Boundaries

**API Boundaries:**

| Direction | Boundary at | Notes |
|---|---|---|
| MCP client → twins-mcp | `spring-ai-starter-mcp-server` (stdio transport) | JSON-RPC over stdin/stdout. Tools are Spring beans; starter dispatches by tool name. |
| twins-mcp → twins REST | `TwinsRestClient` + per-resource `endpoint/` wrappers | All outbound HTTP. RestClient with `TwinsHeadersInterceptor` (`DomainId` + `AuthToken`, ARCH-7) + retry (ARCH-15). |
| twins-mcp → M2M auth | `TwinsM2MClient` | Owns token lifecycle. Single ingress point; no other component calls `/auth/m2m/token/v1`. |

**Component Boundaries:**

- **Tools never construct `RestClient` calls directly.** They go through `TwinsRestClient` → `endpoint/*` wrappers. This keeps retry/auth/domain-id enforcement in exactly one place.
- **Tools never build raw `ToolResponse`.** They use `ToolResponseBuilder` (markdown + structuredContent + size cap + cursor). One pipeline, consistent shape.
- **Tools never throw `RuntimeException`.** They throw domain exceptions (`TwinsPermissionDenied`, `TwinsNotFound`, …); framework's `ErrorEnvelopeMapper` converts. Tools cannot bypass error sanitisation.
- **Tools never log directly via `System.err` or raw logger.** They use the SLF4J logger which is wired through the secrets sanitiser (ARCH-8).

**Service Boundaries:**

- **One Spring context, one process.** No internal service-to-service IPC, no module separation, no `module-info.java` in v1. JPMS is deferred — Boot 4 itself is still transitioning.

**Data Boundaries:**

- **No persistent state.** All data lives in twins. In-memory caches: `TokenHolder` (always on, TTL = `expires_in − 60 s`), `GlossaryClassResolver` (TTL 5 min, opt-out).
- **No DB, no schema, no migrations.** Architecture cannot accidentally leak persistence.

### Requirements to Structure Mapping

**Feature/FR Mapping:**

| FR | Primary file(s) |
|---|---|
| FR-TM-001 `list_classes` | `tool/catalog/ListClassesTool.java` + `client/endpoint/TwinClassSearchEndpoint.java` |
| FR-TM-002 `describe_class` (composite) | `tool/catalog/DescribeClassTool.java` + `TwinClassSearchEndpoint` + `TwinClassFieldSearchEndpoint` |
| FR-TM-003 `describe_class_relations` | `tool/catalog/DescribeClassRelationsTool.java` + `LinkSearchEndpoint` |
| FR-TM-004 `describe_class_statuses` | `tool/catalog/DescribeClassStatusesTool.java` + `TwinStatusSearchEndpoint` |
| FR-TM-010 `list_glossary_sections` | `tool/glossary/ListGlossarySectionsTool.java` + `TwinSearchEndpoint` + `GlossaryClassResolver` |
| FR-TM-011 `get_glossary_section` | `tool/glossary/GetGlossarySectionTool.java` + `TwinSearchEndpoint` + `GlossaryClassResolver` |
| FR-TM-012 `get_glossary_term` | `tool/glossary/GetGlossaryTermTool.java` + `TwinSearchEndpoint` + `GlossaryClassResolver` |
| FR-TM-020 Connection configuration | `config/TwinsConnectionProperties.java` + `config/M2mCredentialsProperties.java` + `app/StartupEnvValidator.java` |
| FR-TM-021 M2M authentication | `client/TwinsM2MClient.java` + `TokenHolder.java` |
| FR-TM-022 Stdio transport | `config/McpServerConfig.java` (uses starter; no custom code) |
| FR-TM-040 Hybrid response shape | `response/ToolResponseBuilder.java` |
| FR-TM-041 Pagination | `response/CursorCodec.java` + `response/SizeCapEnforcer.java` |
| FR-TM-042 Output sanitization | `secrets/SecretsSanitiser.java` + `error/ErrorEnvelopeMapper.java` |
| FR-TM-050 Smithery listing | `smithery.json` + README |
| FR-TM-051 `claude mcp add` snippet | `README.md` |
| FR-TM-052 JSON config block | `README.md` + `claude.json` |
| FR-TM-053 GitHub release artifacts | `.github/workflows/release.yml` + `bootBuildImage` task |
| FR-TM-060 Module layout | `build.gradle` (standalone — D32 overrides PRD wording) |
| FR-TM-061 Spring AI MCP server starter | `config/McpServerConfig.java` + dependency in `build.gradle` |
| FR-TM-062 Reuse of twins components | `build.gradle` dep `com.alcosi.twins:twins-core-dto:1.4.191` (DTO only — D33) |

**Cross-Cutting Concerns:**

| Concern | Location |
|---|---|
| Authentication & session | `client/TwinsM2MClient.java`, `client/TokenHolder.java` |
| Domain isolation (DomainId + AuthToken) | `client/TwinsHeadersInterceptor.java` |
| Permission gate | Inherited server-side via `@ProtectedBy` on twins endpoints (no twins-mcp code) |
| Output pipeline | `response/` (entire package) |
| Error envelope | `error/ErrorEnvelopeMapper.java` |
| Structured logging | `resources/logback-spring.xml` + `secrets/SecretsSanitiser.java` |
| No-deprecated CI gate | `.github/workflows/check-deprecated.yml` + `scripts/check-deprecated-endpoints.sh` + `vendor/twins/` submodule + `docs/allowed-endpoints.md` |
| Prefer-search-endpoints guardrail | Verified at PR review against `docs/allowed-endpoints.md` (manual; CI gate extension is a v1.1 candidate) |
| Read-only surface | `tool/ToolAllowlist.java` (startup check) |
| Composite-tool discipline | Enforced by `TwinsMcpTool` interface contract — tools freely compose `endpoint/` calls |

### Integration Points

**Internal Communication:**

- Spring DI. All beans live in the same context.
- Sequence per tool call:
  1. MCP starter dispatches JSON-RPC → tool bean
  2. `ToolArgsValidator` validates args (JSR-380) → throws `ToolInputInvalid` on failure
  3. Tool calls `TwinsRestClient` (or directly an `endpoint/` wrapper)
  4. RestClient applies `TwinsHeadersInterceptor` (`DomainId` from env, `AuthToken` from `TokenHolder`) + Resilience4j retry
  5. Twins responds → endpoint wrapper deserialises into DTO from `twins-core-dto`
  6. Tool returns DTO(s) to `ToolResponseBuilder`
  7. Builder renders markdown + structuredContent, applies size cap, attaches cursor if list
  8. `ToolMetrics` records counter + latency
  9. Response back to MCP starter → JSON-RPC to stdout
  10. Errors anywhere → `ErrorEnvelopeMapper` catches → error envelope to client; never a stack trace

**External Integrations:**

| External | Purpose | Boundary |
|---|---|---|
| twins REST API | Read domain data (the product's core purpose) | `client/TwinsRestClient` |
| twins M2M auth | Acquire `AuthToken` value | `client/TwinsM2MClient` |
| twins OpenAPI (`/api-docs`) | Optional runtime schema validation (v2 candidate) | None in v1 |
| Smithery registry | Distribution / discovery | `smithery.json` manifest, published at release time |
| GitHub Releases | Binary distribution (JAR + image + checksum) | `.github/workflows/release.yml` |
| Maven Central | Source of `twins-core-dto` and transitive deps | `build.gradle` only |

**Data Flow:**

```
MCP client (Claude Code/Desktop/…)
   │ JSON-RPC over stdio
   ▼
spring-ai-starter-mcp-server  ──►  ToolRegistry  ──►  <ConcreteTool>Bean
                                                       │
                                                       ▼
                                         ToolArgsValidator (JSR-380)
                                                       │
                                                       ▼
                                       TwinsRestClient ──► endpoint wrapper
                                                       │
                                                       ▼
                                       RestClient + DomainIdInterceptor
                                                       │ + AuthToken (TokenHolder)
                                                       │ + Resilience4j retry
                                                       ▼
                                            twins REST (HTTP)
                                                       │
                                                       ▼
                                          DTOs (twins-core-dto JAR)
                                                       │
                                                       ▼
                                         ToolResponseBuilder
                                          ├─ markdown summary
                                          ├─ structuredContent JSON
                                          ├─ SizeCapEnforcer (32 KiB)
                                          └─ CursorCodec (if list)
                                                       │
                                                       ▼
                                          JSON-RPC response → stdout
```

### File Organization Patterns

**Configuration Files:**
- All runtime config via env vars (per ARCH-5). No external YAML profile required for OSS users.
- `application.yml` contains only Spring Boot wiring + sensible defaults; everything operator-tunable is env-var-overridable.
- `application-stage.yml` / `application-release.yml` for twins-internal dev deploys; OSS users use defaults.

**Source Organization:**
- Package-by-feature inside `org.twins.mcp`. Each top-level sub-package is a capability (`app`, `config`, `client`, `tool`, `response`, `error`, `secrets`, `metrics`).
- No `util/` package. If a helper is small enough to be "util", inline it; if it's big enough to need its own package, it belongs to one of the existing capabilities.

**Test Organization:**
- Co-located: `src/test/java/...` mirrors `src/main/java/...`.
- Unit tests: every class with non-trivial logic has a same-name `*Test.java`.
- Integration tests: `*IT.java`, run via `integrationTest` Gradle task; require `TWINS_BASE_URL` env or Testcontainers.
- Fixtures: real captured twins REST responses, stored once per endpoint version. Updates go through PR review.

**Asset Organization:**
- No static assets. Documentation lives under `docs/`. Manifests (`smithery.json`, `claude.json`) at repo root.

### Development Workflow Integration

**Development server:**
- `./gradlew bootRun` — runs twins-mcp against `TWINS_BASE_URL` from local env. Stdio transport — pair with `claude mcp add` for live testing.
- `./gradlew test` — unit tests only (default).
- `./gradlew integrationTest` — spins up twins via Testcontainers, runs `*IT.java`.

**Build process:**
- `./gradlew build` → fat JAR at `build/libs/twins-mcp-<version>.jar`.
- `./gradlew bootBuildImage` → Paketo Docker image (ARCH-19).
- `./gradlew check` → Spotless + Checkstyle + tests; CI gate.

**Deployment:**
- Tag push → GitHub Actions release workflow → fat JAR + Docker image + SHA-256 published to GitHub Releases.
- Smithery picks up from release manifest (`smithery.json`).
- End users install via `claude mcp add` (per README) or pull Docker image.

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:**

| Stack component | Version | Compatible with | Status |
|---|---|---|---|
| Spring Boot | 4.1.0 | Spring AI 2.0.0 (BOM pulls Boot 4.1 by default per Issue #6465) | ✅ |
| Spring AI | 2.0.0 | Boot 4.0/4.1, MCP SDK transitively | ✅ |
| Java | 25 LTS | Boot 4.1 (requires 17+) | ✅ |
| `twins-core-dto` | 1.4.191 | Java 25 — pending OQ-ARCH-1 verification | ⚠️ |
| RestClient | Boot 4 native | sync, no reactive | ✅ |
| Resilience4j | 2.4+ | Boot 4 compatible | ✅ |
| Logback | Boot 4 default | JSON encoder TBD | ✅ |
| `spring-ai-starter-mcp-server` | via BOM | stdio transport, MCP spec 2025-06-18 | ✅ |

No contradictory decisions. All version pairs verified GA as of architecture date.

**Pattern Consistency:**
- Naming patterns (snake_case tool names, PascalCase classes, camelCase methods) align with Java/Spring conventions and MCP convention.
- JSON casing rule (snake_case top-level, twins DTO verbatim nested) is internally consistent and matches D33 (DTO reuse as-is).
- Logging/process patterns align with ARCH-8 sanitiser and ARCH-12 error envelope.

**Structure Alignment:**
- Project tree supports every ARCH-* decision (verified via FR-to-file mapping in Step 6 — 20/20 FRs have a home).
- Boundaries are physically enforced: tools cannot bypass `TwinsRestClient` (no direct `RestClient` injection at tool layer — wired into `endpoint/` wrappers only); tools cannot bypass `ToolResponseBuilder`; tools cannot bypass error mapper.
- **Minor inconsistency:** ARCH-23 mentioned a top-level `infra/` directory; Step 6 tree evolved to package-by-feature under `org.twins.mcp` with `infra/` concerns split across `config/`, `scripts/`, `.github/workflows/`, `docs/`. ARCH-23 is effectively superseded — no doc fix needed since Step 6 is the authoritative structure.

### Requirements Coverage Validation ✅

**Functional Requirements Coverage:** 20/20 FRs mapped to specific files in Step 6 FR-to-file table. No orphan FRs.

**Non-Functional Requirements Coverage:**

| NFR | Covered by | Status |
|---|---|---|
| NFR-TM-001 Performance (p95 budgets) | ARCH-2 (token cache), ARCH-15 (retry budget), ARCH-3 (metadata cache off — risk noted in gaps) | ✅ with monitoring story needed |
| NFR-TM-002 Reliability | ARCH-12 (error envelope), ARCH-15 (retry), ARCH-22 (metrics) | ✅ |
| NFR-TM-003 Secrets | ARCH-8 (sanitiser) | ✅ |
| NFR-TM-004 Multi-tenancy | ARCH-7 (DomainId + AuthToken interceptor, immutable) | ✅ |
| NFR-TM-005 Observability | ARCH-21 (Logback JSON), ARCH-22 (Micrometer local metrics) | ✅ |
| NFR-TM-006 Compatibility | D34 (version pins) | ✅ |
| NFR-TM-007 Backward compat | `docs/allowed-endpoints.md` per-endpoint version pinning (ARCH-20) | ✅ |
| NFR-TM-008 OSS ergonomics | FR-TM-050…053 + README install path | ✅ |

All 8 NFRs architecturally supported.

### Implementation Readiness Validation ✅

**Decision Completeness:**
- 24 ARCH-* decisions documented, each with rationale and PRD/decision-log traceability.
- All 7 OQ-PRD-* resolved with explicit decisions.
- 3 OQ-ARCH-* surfaced; 1 still open (OQ-ARCH-1 — Java 25 compat verification), 2 resolved by story-time inspection (OQ-ARCH-2 → ARCH-20; OQ-ARCH-3 → Spring AI 2.0 uses 1.1.8).

**Structure Completeness:**
- Complete directory tree to file level (Step 6).
- FR-to-file mapping table — every FR has a primary file.
- Integration points and data flow diagrammed.

**Pattern Completeness:**
- 6 critical conflict points addressed (naming, package layout, JSON casing, logging shape, error throwing, tool registration).
- Concrete good-example and anti-pattern code for the tool implementation pattern.
- Enforcement guidelines with CI gates (Spotless, Checkstyle, ADR requirement).

### Gap Analysis Results

**Critical Gaps:** None. Architecture can guide implementation starting from project-init story.

**Important Gaps (non-blocking, resolved at story time):**

| # | Gap | Resolution |
|---|---|---|
| G1 | **OQ-ARCH-1 — Java 25 compat of `twins-core-dto:1.4.191`.** Not verified. | Story-time: first compile under Java 25; if API removals break, pin module to Java 21 or open issue upstream. |
| G2 | **`claude.json` manifest format unspecified.** | Story-time: read MCP install spec, model on Smithery's published `claude_desktop_config.json` examples. |
| G3 | **`smithery.json` manifest format unspecified.** | Story-time: read Smithery publisher docs (post-MVP scope per FR-TM-050). |
| G4 | **`docs/allowed-endpoints.md` initial contents unwritten.** | Story-time: enumerate the 5 v1 endpoints during project-init. |
| G5 | **MapperContext exact param name for `showTwinClassFieldMode=DETAILED`** (OQ-PRD-5 residue). | Story-time: inspect `TwinClassFieldRestDTOMapper` annotations; verify param name. |
| G6 | **Testcontainers strategy for twins** — does twins Docker image work as Testcontainers target? | Story-time: use existing `twins` Docker image (`docker.repo=twins` per `gradle.properties`); integration tests assume Docker available. |
| G7 | **Logback JSON encoder choice** — Logstash encoder dependency vs built-in Boot 4 JSON layout. | Story-time: prefer Boot 4 built-in; fall back to `logstash-logback-encoder` if needed. |
| G8 | **Spring AI 2.0.0 Boot 4.1 BOM quirk (GitHub Issue #6465)** — BOM pulls Boot 4.1.0 transitively; Boot 4.1 is 16 days old as of architecture date. | Mitigation: pin Boot 4.1.0 explicitly in `gradle/libs.versions.toml`; watch for Spring AI 2.0.x patches. |

**Nice-to-Have Gaps (deferred):**

- **NG1** — JPMS `module-info.java`. Deferred — Boot 4 itself is still transitioning.
- **NG2** — Concrete load-test methodology for NFR-TM-001 verification. Add as a v1.1 story.
- **NG3** — v2 migration runbook (Streamable HTTP, Boot-5 if it lands, etc.). Add when v2 architecture starts.
- **NG4** — Code example for `TwinsRestClient` + `endpoint/` wrapper pattern (consumer-side example exists in Step 5; producer-side deferred to story).

**Process Gaps (PM action, not architecture):**

- **PG1** — PRD body has stale references after D32/D33/D34/D35: D9/D18/FR-TM-060/FR-TM-062/§Integration/NFR-TM-006, plus FR-TM-021 + NFR-TM-004 reference `X-Domain-Id` header (wrong — twins uses `DomainId`, see D35). PM (John) should fold back after architecture completes. Stories written before PM update must reference this architecture document as authoritative.

### Validation Issues Addressed

During validation, the following small issues were caught and resolved in-place:

1. **`TWINS_M2M_LOGIN` / `TWINS_M2M_SECRET` env vars in PRD FR-TM-020 [A1]** — wrong per twins codebase (`AuthM2MLoginRqDTOv1` uses `clientId` / `clientSecret`). Corrected in ARCH-5. PM action: update PRD.
2. **`showMode` literal param in PRD FR-TM-002** — does not exist in twins Java code. Real mechanism is `MapperContext` HTTP query params per `docs/rest_api.md`. Corrected in ARCH-17.
3. **`spring-ai-mcp-server-spring-boot-starter` artifact name in PRD** — legacy, abandoned at 1.0.0-M6. Modern name `spring-ai-starter-mcp-server` used throughout architecture. PM action: update PRD body.
4. **`module inside twins` architectural direction (D9/D18)** — overridden by D32. Architecture reflects new direction; PM action: update PRD body.

### Architecture Completeness Checklist

**Requirements Analysis**
- [x] Project context thoroughly analyzed (Step 2)
- [x] Scale and complexity assessed (Step 2)
- [x] Technical constraints identified (Step 2 + Step 3)
- [x] Cross-cutting concerns mapped (Step 2 + Step 6)

**Architectural Decisions**
- [x] Critical decisions documented with versions (Step 4, ARCH-1…24, D32…34)
- [x] Technology stack fully specified (Step 3 + Step 4 version pinning table)
- [x] Integration patterns defined (Step 6 — boundaries + integration points + data flow)
- [x] Performance considerations addressed (ARCH-2, ARCH-15; gap G1 noted for cache tuning)

**Implementation Patterns**
- [x] Naming conventions established (Step 5)
- [x] Structure patterns defined (Step 5 + Step 6)
- [x] Communication patterns specified (Step 5 — no events, logging schema)
- [x] Process patterns documented (Step 5 — error throwing, retry, validation)

**Project Structure**
- [x] Complete directory structure defined (Step 6)
- [x] Component boundaries established (Step 6 — boundaries table)
- [x] Integration points mapped (Step 6 — internal sequence + external table)
- [x] Requirements to structure mapping complete (Step 6 — FR-to-file table, 20/20)

**16/16 checklist items verified.**

### Architecture Readiness Assessment

**Overall Status:** **READY FOR IMPLEMENTATION** (with Important Gaps G1–G8 deferred to story-time; no Critical Gaps; 16/16 checklist items verified).

**Confidence Level:** **High.** Small, well-bounded scope; OSS product (no scale/compliance wildcards); every architectural fork resolved with user input; every OQ has an explicit decision; twins codebase consulted where PRD assumptions needed verification.

**Key Strengths:**

- Every architectural decision traces back to a PRD FR/NFR or a decision-log D-entry — no orphan decisions.
- Read-only surface enforced at **three** layers: `ToolAllowlist` startup check (ARCH-9), tool registry bean discovery, server-side `@ProtectedBy` on twins endpoints. Defense in depth.
- Domain isolation (DomainId) enforced architecturally (HTTP interceptor), not by convention. Tools cannot bypass.
- Secrets sanitiser is at logger boundary, not at every call site. Cannot be bypassed by accident.
- DTO reuse via published Maven artifact (D33) eliminates the binary-incompatibility risk that would have come from sharing a Spring Boot runtime with twins.
- Composite-tool pattern (D10) implemented as a one-method interface — simple to learn, hard to violate.
- CI no-deprecated-endpoints gate (ARCH-20) makes the guardrail mechanical, not aspirational.

**Areas for Future Enhancement:**

- TwinClass metadata cache (ARCH-3) — turn on if NFR-TM-001 budgets missed.
- `/actuator` HTTP endpoint exposure for v2 Streamable HTTP transport.
- JPMS module-info (NG1) — when Boot ecosystem stabilises.
- Load-test harness (NG2) — formal NFR-TM-001 verification.

### Implementation Handoff

**AI Agent Guidelines:**

1. Follow all 24 ARCH-* decisions and D32–D34 entries exactly as documented.
2. Use Step 5 patterns consistently — naming, structure, format, communication, process.
3. Respect Step 6 boundaries — tools never bypass `TwinsRestClient` / `ToolResponseBuilder` / error mapper / logger.
4. Refer to this document as authoritative when PRD body and architecture diverge (PG1 cases).
5. Any new architectural decision ⇒ ADR commit under `docs/adr/NNNN-*.md`.

**First Implementation Priority (Epic 1, Story 1):**

Project initialization. Concretely:

```bash
# In empty twins-mcp/ repo:
mkdir -p src/main/java/org/twins/mcp/{app,config,client,tool,response,error,secrets,metrics}
mkdir -p src/main/resources src/test/java/org/twins/mcp src/test/resources/fixtures
mkdir -p docs/adr scripts .github/workflows
git submodule add <twins-repo-url> vendor/twins
```

Plus: `build.gradle`, `settings.gradle`, `gradle/libs.versions.toml` with Boot 4.1.0 + Spring AI 2.0.0 BOM + `com.alcosi.twins:twins-core-dto:1.4.191` + Java 25 toolchain. `Application.java` with `@SpringBootApplication`. Verifies OQ-ARCH-1 (Java 25 compile) on first build.

Subsequent stories follow the Implementation Sequence from Step 4 (ARCH-1 through ARCH-23).
