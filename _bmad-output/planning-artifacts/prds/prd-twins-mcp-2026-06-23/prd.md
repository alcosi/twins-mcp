---
title: PRD — twins-mcp
status: final
created: 2026-06-23
updated: 2026-06-24
---

# PRD: twins-mcp

*Working title — confirm.*

## 0. Document Purpose

This PRD defines `twins-mcp` — a public, open-source Model Context Protocol (MCP) server that turns any MCP-capable LLM client into a fluent guide over a configured [twins](https://github.com/alcosi/twins) domain. It is intended for the twins maintainer, future external contributors, and downstream BMad workflow owners (Architecture, Epics, Stories).

The document uses **glossary-anchored vocabulary** (§3), **features grouped with FRs nested under them** (§4), and inline `[ASSUMPTION: ...]` tags that are consolidated in §9 for explicit confirmation. It builds on the brainstorming artifact `_bmad-output/brainstorming/brainstorming-session-2026-06-15-1628.md` — decisions from that session are normative where they survive in §6, and superseded where this PRD diverges (recorded in `.decision-log.md` D7, D8, D17–D20).

This PRD does not duplicate twins' own documentation. The twins glossary lives inside each configured domain as Twins of the `TWINS_GLOSSARY` class (see §4.2 and D30); this PRD does not reference the legacy `docs/glossary.md` markdown as a runtime data source.

## 1. Vision

twins is a powerful but concept-dense platform — TwinClasses, Twins, Twinflows, TwinFactories, five permission grant types, multi-tenancy, I18n. Onboarding a new operator (human or agent) to a configured domain today means reading source code, OpenAPI dumps, or making exploratory REST calls. `twins-mcp` collapses that ramp-up: any MCP-capable agent (Claude Code, Claude Desktop, future clients) becomes a **domain guide** that answers free-form questions about *this specific domain* by translating them into the right twins REST calls and returning human-readable answers.

The product has two complementary capabilities bundled in v1:

1. **Domain Catalog** — what TwinClasses exist in a domain, their fields, inter-class relations, statuses.
2. **Terminology** — what twins vocabulary actually means (glossary entries that live in the domain as Twins of a `TWINS_GLOSSARY` class), so the agent's answers are grounded in canonical definitions, not its own reformulations. Storing the glossary inside the domain (rather than in a separate markdown file) means terminology and live domain state stay in sync through the same REST surface.

v1 is intentionally **read-only**, **stdio-only**, **local-first**. The architecture does not preclude Streamable HTTP + OAuth 2.1 for remote deployment in v2 — those are deferred with explicit plug-in points in §6.2.

`twins-mcp` ships as a **Spring Boot module inside the twins project**, reusing twins' auth, multi-tenancy, and OpenAPI configuration. It is published as open source under the same license as twins, distributed via Smithery and GitHub, and installable through the standard `claude mcp add` ergonomics.

## 2. Target User

### 2.1 Jobs To Be Done

- **As a twins maintainer**, I want to ask an agent "what's in this domain?" and get a grounded answer, so I don't have to read source code or run exploratory REST calls every time.
- **As a domain analyst / new operator**, I want to ask "what does *field X on class Y* mean?" and "which classes relate to *Z*?" so I can understand a configured domain without being a twins expert.
- **As an external OSS contributor**, I want to install `twins-mcp` in under five minutes against my own twins instance, so I can experiment and contribute back.
- **As a twins maintainer**, I want glossary entries to live in the same domain as the entities they describe, so a single twins REST call surfaces both the canonical term and the live domain state — no parallel documentation source to keep in sync.

### 2.2 Non-Users (v1)

- **Operators who need write access.** v1 is read-only. No `create_twin`, `trigger_transition`, or any mutating tool exists.
- **Operators of remote/cloud twins without local shell access.** v1 is stdio-only; remote clients (HTTP transport) are v2.
- **Consumers who want permission introspection or transition-effect prediction.** Explicitly deferred to v2 (§6.2).
- **Multi-domain browsing in one session.** One MCP session = one domain (env var `TWINS_DOMAIN_ID`).

### 2.3 Key User Journeys

- **UJ-TM-1.** Nikita, twins maintainer, configures `twins-mcp` for the first time against a local twins dev instance and asks "what classes are in this domain?" via Claude Code.
  - **Path:** Nikita runs `claude mcp add twins-mcp --env TWINS_BASE_URL=http://localhost:8080 --env TWINS_API_TOKEN=... --env TWINS_DOMAIN_ID=... -- java -jar twins-mcp.jar`. Opens Claude Code in a project. Asks the question. The agent calls `list_classes`, sees three TwinClasses, summarizes them in two sentences. Nikita asks follow-up "tell me about the User class"; the agent calls `describe_class` — gets back class metadata + the detailed field list in one call (fields rendered via twins `showMode`). Follow-up "how is it related to other classes?" → `describe_class_relations`; "what statuses can a User be in?" → `describe_class_statuses`.
  - **Climax:** Nikita gets a grounded answer in under 30 seconds without opening the twins source or REST client.
  - **Resolution:** Nikita asks three more questions, all grounded. Keeps the MCP server configured for daily use.

- **UJ-TM-2.** Priya, an external OSS contributor, clones twins, builds `twins-mcp` module, runs it against a demo domain, and asks "what does *Twinflow* mean?" via Claude Desktop.
  - **Path:** Priya follows README install. Asks the question. The agent calls `get_glossary_term("Twinflow")`, gets canonical definition + JPA class path + table name, returns it verbatim with one paraphrase for context.
  - **Climax:** Priya gets the canonical answer, not an LLM hallucination.
  - **Resolution:** Priya reads the linked JPA class, files a PR to improve an unrelated field annotation.

## 3. Glossary

*Terms specific to this PRD. twins-domain terms (TwinClass, Twin, Twinflow, TwinFactory, Link, TwinLink, Domain, etc.) are defined in twins' canonical `docs/glossary.md` and used here verbatim.*

- **MCP** — Model Context Protocol; the spec at https://modelcontextprotocol.io that defines how LLM clients discover and call server-provided tools.
- **MCP client** — any spec-conformant client (Claude Code, Claude Desktop, Claude.ai, ChatGPT, future clients) that connects to `twins-mcp`.
- **MCP tool** — a domain-oriented callable exposed by `twins-mcp` (e.g., `list_classes`, `describe_class`, `get_glossary_term`). Tools internally compose one or more twins REST calls. Not 1-to-1 with REST endpoints.
- **MCP transport** — wire mechanism between client and server. v1 ships **stdio**; v2 adds **Streamable HTTP**. HTTP+SSE is deprecated in MCP spec (2025-03-26) and is not used.
- **Domain Catalog** — v1 capability exposing TwinClasses + fields + relations + Twinflow + TwinFactory of one configured domain.
- **Terminology** — v1 capability exposing the twins glossary as it lives in the configured domain: Twins of the operator-provisioned `TWINS_GLOSSARY` class (see §4.2, D30). Tools read them via the standard twin search endpoint; no markdown artifact is bundled.
- **MCP session** — one running `twins-mcp` process bound to one twins instance (via `TWINS_BASE_URL`) and one domain (via `TWINS_DOMAIN_ID`).
- **Service account** — a twins M2M account (auth via `POST /auth/m2m/token/v1`) used by `twins-mcp` to authenticate to twins REST. Provisioned with read-only `@ProtectedBy` grants by the operator; no token-level scopes exist in twins. One service account per domain (one MCP session = one domain = one account).
- **Smithery** — de facto public MCP server registry as of mid-2026 (`smithery.ai`). `twins-mcp` is listed there for discovery and one-command install.

## 4. Features

*FRs are numbered globally with the `FR-TM-NNN` prefix so downstream artifacts (architecture, epics, stories) have stable references.*

### 4.1 Domain Catalog Tools

**Description:** A set of domain-oriented MCP tools that, together, allow an agent to navigate the structure of a configured domain: enumerate TwinClasses, drill into one TwinClass (with fields, relations, statuses), and follow links to related classes. Realizes UJ-TM-1.

Tools may compose multiple REST calls internally — `describe_class` issues one call for class metadata and one for the field list (with `showMode` for detailed field rendering). Tools use search endpoints where a search variant exists for the resource (see "Prefer search endpoints" guardrail in §Constraints and Guardrails). This composition pattern is the conventional MCP server design (D10) — it is *not* a leaky per-endpoint wrapper. Twinflow is intentionally untouched in v1 (D24).

**Functional Requirements:**

#### FR-TM-001: `list_classes` tool

The agent can list all TwinClasses in the configured domain, paginated, via `POST /private/twin_class/search/v2`. Realizes UJ-TM-1.

**Consequences (testable):**
- Tool returns page size ≤ 100 TwinClasses per call; default page size 20.
- Tool accepts `query` (free-text filter on class name/key), `page`, `pageSize` arguments.
- Tool response is a hybrid (markdown summary table + structured JSON `structuredContent` with cursor).
- If twins returns zero classes, tool returns an empty page with a "no TwinClasses match" markdown note, not an error.

**Out of Scope:**
- Sorting beyond what `/private/twin_class/search/v2` exposes natively.

#### FR-TM-002: `describe_class` tool

The agent can get full detail of one TwinClass by `id` or by `key` (resolved via `list_classes` first): class metadata (name, description, key) plus the field list with detailed per-field descriptions. The tool composes two REST calls internally — class metadata via view-by-id (no search equivalent for single-class fetch) and fields via search with a `showMode` query parameter that triggers detailed field rendering. Realizes UJ-TM-1.

**Consequences (testable):**
- Tool calls `GET /private/twin_class/{twinClassId}/v1` (or `/private/twin_class_by_key/{key}/v1` when called by key) for class metadata.
- Tool calls `POST /private/twin_class_fields/search/v2` with `search.twinClassIdMap = {<twinClassId>: true}` and a `showMode` query parameter that causes twins to render each field's full detail (type, validation, defaults, descriptor, permission IDs, i18n). Exact showMode value resolved at architecture time (see OQ-PRD-5).
- Tool response combines: class metadata block + flat list of field details.
- If the class does not exist or is denied for the service account, tool returns a structured error with the underlying twins error code, not a stack trace.
- Permissions: `TWIN_CLASS_VIEW` (for class metadata) + `TWIN_CLASS_FIELD_VIEW` (for field search). Service account must carry both.

**Out of Scope:**
- Resolving permission IDs to grants ("who can edit field X?") — v2.

#### FR-TM-003: `describe_class_relations` tool

The agent can list inter-class relations (Links) for one TwinClass by calling `POST /private/link/search/v2` with `search.srcOrDstTwinClassIdList = [<twinClassId>]`. Returns a flat list of `LinkDTOv2` covering both directions (the agent filters client-side by `srcTwinClassId` vs `dstTwinClassId` to know which side the queried class is on). Realizes UJ-TM-1.

**Consequences (testable):**
- Tool accepts `twinClassId` (or `twinClassKey` resolved via `list_classes` first) plus optional `direction` (`src` / `dst` / `both`, default `both`), `linkType`, `page`, `pageSize`.
- Tool maps `direction=src` → `search.srcTwinClassIdList`, `direction=dst` → `search.dstTwinClassIdList`, `direction=both` → `search.srcOrDstTwinClassIdList`.
- Tool response enumerates each link with: id, src/dst TwinClass IDs, forward/backward names (i18n), `LinkType`, `LinkStrength`, and any related-object enrichment from the twins response.
- **Per-endpoint cap divergence:** source DTO `LinkSearchDTOv1.srcTwinClassIdList` and friends are capped at `@Size(max = 50)`, so this tool refuses `pageSize > 50` (stricter than the general 100-item cap in FR-TM-041) when calling `/private/link/search/v2`.
- Tool response is paginated per FR-TM-041; default page size 20.
- If the TwinClass has zero relations, tool returns an empty page with a "no relations" markdown note, not an error.
- Permission: `@ProtectedBy({LINK_MANAGE, LINK_VIEW})` → service account must carry `LINK_VIEW` grant (provisioning guide).

**Out of Scope:**
- Recursively expanding related classes beyond direct neighbors — the agent can chain `describe_class` calls on its own.
- TwinLink instance enumeration (links between actual Twins, not TwinClasses) — out for all versions in v1's read-only class-catalog scope.

#### FR-TM-004: `describe_class_statuses` tool

The agent can list the TwinStatus entries defined for one TwinClass by calling `POST /private/twin_status/search/v2` with `search.twinClassIdMap = {<twinClassId>: true}`. No Twinflow lookup is performed — statuses are queried directly. Realizes UJ-TM-1.

**Consequences (testable):**
- Tool accepts `twinClassId` (or `twinClassKey` resolved via `list_classes` first) plus optional `page`, `pageSize`, and a `keyLike` free-text filter.
- Tool response enumerates the matching `TwinStatusEntity` rows: id, key, name (i18n-resolved), description (i18n-resolved), `inheritable` flag.
- Tool response is paginated per FR-TM-041; default page size 20, max 100.
- If the TwinClass has zero statuses, tool returns an empty page with a "no statuses" markdown note, not an error.
- If the TwinClass does not exist or is denied for the service account, tool returns a structured error with the underlying twins error code (403 / 404 mapped accordingly), not a stack trace.

**Out of Scope:**
- Twinflow lookup of any kind — entirely v2 (D24). The tool does not call `/private/twinflow/{id}/v1` and does not return Twinflow metadata.
- Transition enumeration ("which transitions exist between statuses?") — v2 (D20).
- Predicting the effect of triggering a transition ("what happens if I trigger Y?") — v2 (D20).

### 4.2 Terminology Tools

**Description:** MCP tools that expose the twins glossary so agent answers cite canonical definitions rather than LLM paraphrases. The glossary lives in the configured twins domain as a set of Twins whose TwinClass key is `TWINS_GLOSSARY` — each Twin is one glossary entry. Tools read these Twins via the standard twin search endpoint. Realizes UJ-TM-2.

The `TWINS_GLOSSARY` TwinClass is provisioned by the operator (see provisioning guide). If the class is not present in the domain, the terminology tools return an empty result with a "glossary not configured" note — not an error.

**Functional Requirements:**

#### FR-TM-010: `list_glossary_sections` tool

The agent can enumerate the distinct glossary sections (grouping key over the glossary Twins) by calling `POST /private/twin/search/v2` filtered to the resolved `TWINS_GLOSSARY` class. The grouping field (e.g., a `section` field on the class) is resolved at architecture time against the actual field schema of the `TWINS_GLOSSARY` class in the target domain — see OQ-PRD-7. Realizes UJ-TM-2.

**Consequences (testable):**
- Tool resolves `TWINS_GLOSSARY` class ID from class key (via `list_classes` or `/private/twin_class_by_key/{key}/v1`).
- Tool issues `POST /private/twin/search/v2` with `twinClassIdList = [<classId>]` and aggregates the grouping field's distinct values from the returned Twins.
- Tool response lists each section with one-line description (where applicable) and entry count.
- If the `TWINS_GLOSSARY` class is missing from the domain, tool returns a "glossary not configured" markdown note, not an error.
- Pagination is honored per FR-TM-041 — if total glossary Twins exceed one page, tool warns that the section list is partial.

#### FR-TM-011: `get_glossary_section` tool

The agent can fetch all glossary entries of one section by name. Realizes UJ-TM-2.

**Consequences (testable):**
- Tool resolves the `TWINS_GLOSSARY` class ID, then issues `POST /private/twin/search/v2` filtered by both `twinClassIdList` and the section value (via twin's serialized field data; exact filter mechanism depends on the class's field schema — OQ-PRD-7).
- Tool response enumerates each glossary entry with the fields exposed by the `TWINS_GLOSSARY` class (term name, definition, optional JPA class path, optional table name, optional notes — whatever the operator-provisioned schema defines).
- Tool response is paginated per FR-TM-041.

#### FR-TM-012: `get_glossary_term` tool

The agent can fetch one glossary entry by term name (case-insensitive, partial match via `twinNameLikeList`). Realizes UJ-TM-2.

**Consequences (testable):**
- Tool resolves the `TWINS_GLOSSARY` class ID, then issues `POST /private/twin/search/v2` with `twinClassIdList = [<classId>]` and `twinNameLikeList = [<normalized query>]`.
- Tool returns the single best match (or zero matches with "did you mean…?" suggestions surfaced from a secondary fuzzy search if no exact match).
- Tool response includes the entry's fields as defined by the `TWINS_GLOSSARY` class.

**Out of Scope:**
- Cross-referencing glossary entries against a live TwinClass instance (e.g., "show me the Twinflow entry *and* the actual Twinflow of class X") — agent composes tools itself.
- Write access to glossary Twins — out for all versions (read-only product).
- Field schema of `TWINS_GLOSSARY` — operator decides; documented in provisioning guide, not fixed by this PRD.

### 4.3 Connection & Auth

**Description:** How `twins-mcp` connects to a twins instance and authenticates. v1 uses env-var configuration, stdio transport, and the existing twins M2M auth flow. Realizes UJ-TM-1, UJ-TM-2.

**Functional Requirements:**

#### FR-TM-020: Connection configuration

`twins-mcp` reads connection configuration from environment variables at process start:

- `TWINS_BASE_URL` — twins instance root URL (e.g., `http://localhost:8080`).
- `TWINS_DOMAIN_ID` — the domain to scope all queries to (one MCP session = one domain; §2.2).
- `TWINS_M2M_LOGIN` and `TWINS_M2M_SECRET` (or equivalent credentials shape expected by `POST /auth/m2m/token/v1`) — credentials for the service account.

`[ASSUMPTION: credential shape and var names to be confirmed with twins M2M controller at architecture time. The exact body fields of `AuthM2MLoginRqDTOv1` are not yet read by this PRD.]`

Realizes UJ-TM-1, UJ-TM-2.

**Consequences (testable):**
- Process refuses to start with a clear env-var error message if any required var is missing or malformed.
- `TWINS_BASE_URL` must include scheme; trailing slash optional.
- Process validates that `TWINS_BASE_URL` is reachable on start (with a short timeout); emits a clear error if not, rather than failing lazily on first tool call.

#### FR-TM-021: M2M authentication

`twins-mcp` authenticates to twins via `POST /auth/m2m/token/v1` using the configured service account credentials, obtains an `AuthM2MTokenRsDTOv1`, and uses the resulting token as a Bearer credential for all subsequent REST calls. Token refresh is handled automatically before expiry.

**Consequences (testable):**
- Token is cached in process memory only; never written to disk; never logged (NFR-TM-003).
- On 401 from twins, `twins-mcp` refreshes once; on repeated 401, returns a structured error to the MCP client.
- All REST calls include `X-Domain-Id` header set to `TWINS_DOMAIN_ID`.

#### FR-TM-022: Stdio transport (v1)

`twins-mcp` v1 ships stdio transport only, conforming to MCP spec 2025-06-18. The transport implementation is provided by `spring-ai-mcp-server-spring-boot-starter` (D9, D18).

**Consequences (testable):**
- Process reads JSON-RPC messages from stdin, writes to stdout, logs to stderr (never stdout — stdout is the protocol channel).
- Process exits cleanly on `SIGTERM` / `Ctrl-C` from the parent MCP client.

**Out of Scope (v1):**
- Streamable HTTP transport — v2. The `TWINS_MCP_TRANSPORT` env var named in the brainstorming artifact is **reserved** for v2 (selects `stdio` vs `streamable-http`); v1 hard-codes stdio and the var is ignored if set.
- OAuth 2.1 + DCR + CIMD for remote servers — v2.

### 4.4 Return Shape, Pagination & Output Hygiene

**Description:** Contract for every tool's response shape. Grounded in MCP ecosystem research (D13) — some MCP clients silently truncate oversize results or hard-fail.

**Functional Requirements:**

#### FR-TM-040: Hybrid response shape

Every tool response is a hybrid:

1. **Markdown summary** (top) — concise, agent-scannable, 5–30 lines. For lists: a table. For single entities: a key-value block.
2. **Structured JSON** (bottom, as MCP `structuredContent`) — full DTO data, parseable by clients that consume structured output.

**Consequences (testable):**
- Markdown summary never duplicates more than 30% of structured JSON (avoid context bloat).
- Structured JSON keys match twins DTO field names (snake_case if DTO uses snake_case, camelCase if camelCase — confirm at architecture time).
- Single tool response total size ≤ 32 KiB; over-cap responses paginate (FR-TM-041).

`[ASSUMPTION: 32 KiB cap is a reasonable default per MCP ecosystem guidance; exact value confirmed at architecture time.]`

#### FR-TM-041: Pagination

List-returning tools paginate with an opaque cursor returned in both markdown ("next page: pass cursor=…") and structured JSON (`nextCursor` field).

**Consequences (testable):**
- Cursor is opaque (not a raw offset); the agent passes it back unchanged.
- Tools refuse `pageSize > 100`; default `pageSize = 20`.

#### FR-TM-042: Output sanitization

No tool response, log line, or error message ever contains: `TWINS_API_TOKEN`, M2M credentials, Bearer tokens, or any value marked sensitive in twins DTOs (`@Schema(accessMode = READ_ONLY)` fields are followed).

**Consequences (testable):**
- All log lines pass through a sanitizer that redacts known-secret patterns.
- Error responses to the MCP client include twins error codes and messages, but never stack traces or auth headers.

### 4.5 Distribution & Discovery

**Description:** How external consumers discover and install `twins-mcp`. Public OSS distribution is the v1 bar (D4). Realizes UJ-TM-2.

**Functional Requirements:**

#### FR-TM-050: Smithery listing

`twins-mcp` is listed on Smithery with install metadata, env-var schema, and a one-paragraph description. Realizes UJ-TM-2.

**Consequences (testable):**
- Smithery `package.json` (or equivalent manifest) is committed to the twins repo.
- Listing links back to GitHub repo and README.

#### FR-TM-051: `claude mcp add` snippet in README

README contains a copy-paste `claude mcp add` snippet for stdio transport, including the three required env vars from FR-TM-020. Realizes UJ-TM-1, UJ-TM-2.

**Consequences (testable):**
- Snippet runs as-is on a fresh shell with Java 21 installed.
- README documents the Java 21 dependency and minimum twins REST API version.

#### FR-TM-052: JSON config block for `claude_desktop_config.json`

README contains a JSON snippet for Claude Desktop config (manual install path for users who don't use `claude mcp add`).

#### FR-TM-053: GitHub release artifacts

Each release publishes: a fat JAR, a Docker image, and a SHA-256 checksum. Release notes document any new env vars, breaking changes, and twins REST API version compatibility.

### 4.6 Module Integration

**Description:** `twins-mcp` is a Spring Boot module inside the twins project, not a separate service. It uses the official Java MCP SDK via `spring-ai-mcp-server-spring-boot-starter` (D9, D18).

**Functional Requirements:**

#### FR-TM-060: Module layout

`twins-mcp` lives as a Gradle submodule of twins (e.g., `modules/twins-mcp/`). It depends on twins core for shared DTO classes, auth client, and OpenAPI configuration.

**Consequences (testable):**
- Module builds as part of the standard twins `./gradlew build`.
- Module can also build standalone (for distribution) producing a fat JAR with all dependencies.

#### FR-TM-061: Spring AI MCP server starter usage

Module uses `spring-ai-mcp-server-spring-boot-starter` for MCP protocol handling. Tools are registered as Spring beans annotated per the starter's conventions.

#### FR-TM-062: Reuse of twins components

Module reuses twins' REST client configuration, M2M auth client, DTO classes (no parallel DTO layer), and OpenAPI metadata where useful (e.g., for runtime schema validation).

**Consequences (testable):**
- No duplicate DTO definitions between `twins-mcp` and twins core. twins' DTO conventions (`@Schema`, `*DTOv1` suffixes, `@RelatedObject` UUID resolution) are followed verbatim.
- twins' auth, multi-tenancy, and permission infrastructure is used as-is — not reimplemented. Concretely: the M2M token materializes an `ApiUser` thread-local for in-process calls; all entity reads flow through `findEntitySafe()` with `isEntityReadDenied()` gate; `@ProtectedBy` enforces view-grants on every endpoint touched by `twins-mcp` tools.

## 5. Non-Goals (Explicit)

- **Not a write surface.** No mutating tool ever ships under `twins-mcp` — not in v1, not in v2. Write operations stay in twins REST directly.
- **Not a multi-domain browser.** One MCP session is bound to one domain.
- **Not a twins client SDK.** The Java client code is internal; consumers use the MCP tools or REST.
- **Not an agent.** `twins-mcp` exposes tools; the LLM client orchestrates them. No prompt templates, no system prompts, no agent loop in v1.
- **Not an HTTP/SSE server.** Deprecated transport; never shipped.
- **Not a generic ORM-over-MCP.** Tools are domain-oriented, twins-specific.
- **Not exposing TwinFactory pipelines.** Concept is not in the current v1 target scope (D23); no `describe_class_factories` tool now. Revisit when the concept matures in twins.

## 6. MVP Scope

### 6.1 In Scope (v1)

- Stdio transport only.
- Connection via env vars (`TWINS_BASE_URL`, `TWINS_DOMAIN_ID`, M2M credentials).
- M2M auth against `POST /auth/m2m/token/v1`.
- Domain Catalog tools: `list_classes`, `describe_class`, `describe_class_relations`, `describe_class_statuses` (FR-TM-001 … FR-TM-004). `describe_class` composes class-metadata view + field search (with `showMode` for detail) in one tool. Twinflow entirely out of v1 (D24).
- Terminology tools: `list_glossary_sections`, `get_glossary_section`, `get_glossary_term` (FR-TM-010 … FR-TM-012). Source: `POST /private/twin/search/v2` filtered to TwinClass key `TWINS_GLOSSARY` (operator-provisioned class). No bundled glossary artifact.
- Hybrid response shape, pagination, output sanitization (FR-TM-040 … FR-TM-042).
- Distribution: Smithery listing, `claude mcp add` snippet, JSON config, GitHub releases (FR-TM-050 … FR-TM-053).
- Spring Boot module inside twins project (FR-TM-060 … FR-TM-062).

### 6.2 Out of Scope for MVP

- **Streamable HTTP transport (v2).** v1 = stdio only. v2 adds Streamable HTTP per MCP spec 2025-06-18.
- **OAuth 2.1 + PKCE + DCR + CIMD (v2).** Required for public remote MCP per spec; deferred until remote transport ships. v1 architecture must not preclude it (D19).
- **Permission introspection capability (v2).** "Who can edit field X?" — requires proxy logic since twins has no field-level endpoint (D11). Plug-in point: a new `explain_field_permissions` tool that composes `describe_class` + permission grant lookup.
- **Twinflow exposure (v2).** The entire Twinflow concept — Twinflow lookup, transition enumeration, transition-effect prediction — is v2 (D24). v1's `describe_class_statuses` queries the TwinStatus table directly via `POST /private/twin_status/search/v2` and never calls `/private/twinflow/{id}/v1`. Plug-in points for v2: a new `describe_class_twinflow` tool, plus extending `describe_class_statuses` with optional transition context.
- **Transition-effect prediction (v2).** "What happens if I trigger Y?" — requires modeling transition side effects. Plug-in point: a new `predict_transition` tool.
- **TwinFactory exposure.** Concept not in current scope (D23); revisit when the concept matures in twins. If reintroduced, likely as a `describe_class_factories` tool calling `/private/factory/{id}/v1`.
- **`.well-known/mcp` discovery endpoint (v2).** Reserve the URL in v1 docs; implement in v2 alongside HTTP transport.
- **Multi-domain browsing (v2+).** Future: domain switcher tool, but explicit design needed for security.
- **Telemetry / usage analytics (v2+).** v1 has no remote telemetry; opt-in local metrics only.
- **`TWINS_GLOSSARY` provisioning automation.** The operator creates the class manually in their domain (recommended schema in provisioning guide). No "seed glossary" importer or migration ships with `twins-mcp` v1.

`[NOTE FOR PM]` Permission introspection (D11) and transition prediction (D20) are the user's stated future vision — both named explicitly in the original brain dump as capabilities the agent should eventually offer. Keep them visible in roadmap reviews; do not silently abandon.

## 7. Success Metrics

**Primary**

- **SM-TM-1**: Adoption — Smithery installs + GitHub release downloads per month. Target: 100 unique installs in first 90 days post-v1 release. Validates FR-TM-050, FR-TM-053.
- **SM-TM-2**: Install success rate — % of new users who complete the README install path and successfully call `list_classes` within 10 minutes. Target: ≥ 70%. Validates FR-TM-051, FR-TM-020, FR-TM-021.

**Secondary**

- **SM-TM-3**: Engagement — average tool calls per active session. Target: ≥ 4 (indicates the agent finds the tool surface useful). Validates FR-TM-001 … FR-TM-004, FR-TM-010 … FR-TM-012.
- **SM-TM-4**: Glossary citation rate — % of agent answers about twins concepts that cite the canonical glossary definition. Target: ≥ 50%. Measured by sampling 20 random agent sessions per month and reviewing whether glossary-grounded terminology was used. Validates FR-TM-010 … FR-TM-012.

**Counter-metrics (do not optimize)**

- **SM-TM-C1**: Tool error rate — % of tool calls returning a structured error. *Do not* drive this to zero by silently swallowing errors. Counterbalances SM-TM-3: high engagement with low error rate is good; high engagement with hidden errors is bad.
- **SM-TM-C2**: Response size — average tool response size. *Do not* inflate by dumping raw DTOs. Counterbalances SM-TM-3 and SM-TM-4: bigger responses can look like "more value" but burn agent context. Cap enforced by FR-TM-040, FR-TM-041.

## 8. Open Questions

1. **OQ-PRD-1.** Exact shape of `AuthM2MLoginRqDTOv1` and corresponding env var names (FR-TM-020 `[ASSUMPTION]`). Resolve at architecture phase by reading `AuthM2MTokenController.java`.
2. **OQ-PRD-2.** Response size cap value — 32 KiB default proposed (FR-TM-040 `[ASSUMPTION]`). Confirm against real twins DTO sizes during architecture spike.
3. **OQ-PRD-3.** Versioning: should `twins-mcp` ship inside a twins release or independently? Affects release cadence and twins-version compatibility surface (FR-TM-060).
4. **OQ-PRD-4.** How does the "no deprecated endpoints" CI check access the twins source tree at scan time? Options: submodule pin, sibling-repo path, or vendored allow-list. Decided at architecture phase (see Constraints and Guardrails).
5. **OQ-PRD-5.** Which exact `showMode` value(s) on `/private/twin_class_fields/search/v2` cause twins to render each field's full detail (type, validation, defaults, descriptor, permission IDs, i18n)? Resolved at architecture spike by reading `showMode` definitions in twins core.
6. **OQ-PRD-6.** Field schema of the `TWINS_GLOSSARY` TwinClass. The operator provisions this class; the PRD assumes a flexible schema. Architecture phase documents a recommended schema (term name, definition, section, optional JPA path, optional table, optional notes) that operators can follow, and the provisioning guide advertises it. Tools must not assume any field beyond the bare minimum (Twin name + serialized field map).
7. **OQ-PRD-7.** How does `list_glossary_sections` group glossary Twins into sections at runtime — distinct values of a designated field, or a separate "section" TwinClass linked to glossary entries? Resolved at architecture phase against the recommended schema from OQ-PRD-6.

## 9. Assumptions Index

- **[A1]** §4.3 FR-TM-020 — Credential env var shape (`TWINS_M2M_LOGIN`, `TWINS_M2M_SECRET`) inferred; confirm against twins M2M controller.
- **[A2]** §4.4 FR-TM-040 — 32 KiB response cap is a reasonable default per MCP ecosystem guidance; confirm at architecture spike.
- **[A3]** §4.4 FR-TM-040 — Structured JSON key casing follows twins DTO conventions; not yet verified field-by-field.
- **[A4]** §4.5 FR-TM-051 — `claude mcp add` snippet assumes Java 21 is the only system prerequisite; if twins-mcp needs additional native deps, README must list them.
- **[A5]** §6.2 — Plug-in points for v2 capabilities (`explain_field_permissions`, `predict_transition`) are described at the PRD level; exact REST compositions TBD at v2 architecture time.

---

## Cross-Cutting NFRs

- **NFR-TM-001 (Performance).** `list_classes` p95 ≤ 2s; `describe_class*` p95 ≤ 3s; glossary tools p95 ≤ 500ms over a warm REST connection (local-only; no extra REST round-trip beyond the twin search call). Measured against a domain with ≤ 100 TwinClasses.
- **NFR-TM-002 (Reliability).** `twins-mcp` degrades gracefully when twins is unreachable: structured error to MCP client, no crash, automatic retry on next tool call.
- **NFR-TM-003 (Security — secrets).** Credentials (`TWINS_*` env vars, M2M tokens, Bearer tokens) never appear in: logs, tool responses, error messages, or stack traces. Sanitizer enforces.
- **NFR-TM-004 (Security — multi-tenancy).** All REST calls include `X-Domain-Id` set to `TWINS_DOMAIN_ID`; no tool can override it. The MCP server cannot be tricked into querying another domain via tool arguments.
- **NFR-TM-005 (Observability).** Structured logs to stderr (JSON format), with secrets redacted. Basic metrics exposed locally (counters per tool, latency histogram) — no remote telemetry in v1.
- **NFR-TM-006 (Compatibility).** Java 21 with `--enable-preview` (matches twins). Spring Boot 3.5+. MCP spec 2025-06-18.
- **NFR-TM-007 (Backward compatibility).** `twins-mcp` v1 pins to twins REST endpoint versions `v1` / `v2` as enumerated in §4. twins uses endpoint versioning; `twins-mcp` follows the same per-endpoint version pinning and tracks deprecations.
- **NFR-TM-008 (OSS ergonomics).** A new user with Java 21 installed can go from clone to first successful `list_classes` call in under 10 minutes following the README only. Validates SM-TM-2.

## Constraints and Guardrails

- **Read-only surface.** No mutating tool. Enforced at code-review gate.
- **Prefer search endpoints.** When a twins resource has both a view-by-id endpoint and a search endpoint, `twins-mcp` tools MUST use the search endpoint. Search endpoints support pagination, server-side filtering, sorting, and richer DTO enrichment — the view-by-id variants return a single unfiltered entity with no pagination path. View-by-id endpoints are only used when no search equivalent exists (e.g., class metadata lookup via `/private/twin_class/{id}/v1` has no search equivalent for a single-class fetch — permitted). This rule is verified at PR review against the allowed-endpoint inventory.
- **No deprecated endpoints.** `twins-mcp` tools MUST NOT call any twins REST endpoint whose handler method or controller class is annotated `@Deprecated`. Enforced by:
  - **CI check** that scans `twins-mcp`'s declared endpoint inventory against the twins codebase `@Deprecated` markers; PR fails if a forbidden call is introduced.
  - **Release gate** that re-runs the same check at release time. If a previously-used endpoint is deprecated upstream between releases, the release is blocked until the tool migrates to the non-deprecated alternative (or the tool is removed/feature-flagged off).
  - Per-endpoint list of allowed endpoints is committed to the `twins-mcp` repo and updated through PR review.
- **One domain per session.** Env-var bound, not tool-argument bound.
- **MCP spec compliance.** Stdio transport implementation must pass the official MCP spec test suite for 2025-06-18 before each release.
- **License parity.** `twins-mcp` ships under the same license as twins.

## Versioning and Deprecation Policy

- `twins-mcp` uses semver. Breaking changes to tool names, arguments, or response shape require a major bump.
- Deprecation of an MCP tool requires one minor release of dual-support (deprecated + new) before removal.
- twins upstream endpoint deprecations are tracked in `.decision-log.md`; affected tools get a `[DEPRECATION NOTE]` in their markdown summary until migrated (e.g., FR-TM-003 today).

## Language / Runtime Targets and Dependency Policy

- Java 21 (LTS) with `--enable-preview` flag, matching twins.
- Spring Boot 3.5+.
- `spring-ai-mcp-server-spring-boot-starter` (version pinned at architecture time).
- No native-image requirement in v1; v2+ may add GraalVM for smaller container footprint.

## Integration and Dependencies

- **twins REST API** (in-process, same JVM via shared Spring context — preferred — or HTTP loopback to `TWINS_BASE_URL`). Decision recorded at architecture time.
- **twins OpenAPI** at `/api-docs` — consumed at runtime for response schema validation (optional in v1; mandatory in v2 if dynamic tools are added).
- **MCP spec 2025-06-18** — `twins-mcp` declares spec compatibility in its server metadata.

## Risk and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Any v1 endpoint `@Deprecated` upstream between releases | Medium | High (release blocked per "no deprecated endpoints" guardrail) | Release-gate CI scan; per-release migration PR if a used endpoint gets deprecated |
| M2M has no scopes — service account misconfigured with write grants | Medium | High (violates read-only NFR) | Document provisioning guide in README; runtime assertion that no write tool is registered |
| MCP spec evolves (Streamable HTTP shape, OAuth details) | High (1-2 yr horizon) | Medium | Pin MCP spec version; v2 architecture reviews spec changes before adoption |
| Twins REST API version drift | Low | Medium | Per-endpoint version pinning; track deprecations |
| Glossary not provisioned in target domain (operator never created `TWINS_GLOSSARY` class) | Medium | Low (terminology tools return empty) | Tools return "glossary not configured" note instead of error; provisioning guide documents the recommended class schema; README's install path checks for the class |
| Operator's `TWINS_GLOSSARY` class schema diverges from documented recommended schema (e.g., no `section` field) | Medium | Medium (`list_glossary_sections` may degrade) | Tools must not hard-assume field names beyond Twin name + serialized map; OQ-PRD-7 resolves the section grouping mechanism; provisioning guide shows the recommended schema as a copy-paste template |
| External contributor submits a write tool | Low | High | PR template + CODEOWNERS + CI check that rejects tools outside the read-only allowlist |
