# Story 1.7: `list_classes` Tool End-to-End

Status: ready-for-dev

## Story

As an **MCP client (e.g., Claude) connected via stdio**,
I want **a `list_classes` tool that lists TwinClasses in the configured domain, paginated, with markdown + structured JSON output**,
so that **I can answer the user's question "what entity types exist in this domain?" with grounded data**.

## Acceptance Criteria

1. **AC-1 (happy path)** — Twins reachable, domain has TwinClasses. MCP client calls `list_classes` with no args → tool POSTs to `/private/twin_class/search/v2` with `{query: null, page: 0, pageSize: 20}`. Response is hybrid: markdown table (`| key | name | id | brief |`) + `structuredContent` JSON with the twins DTO field names verbatim. Total response ≤ 32 KiB.
2. **AC-2 (pagination)** — More than 20 TwinClasses → response includes `nextCursor` in `structuredContent` (opaque base64 per ARCH-13); markdown summary notes the cursor. Passing `cursor=<value>` on the next call returns page 2. Filter-hash mismatch (cursor issued for filter X, called with filter Y) → `TOOL_INPUT_INVALID`.
3. **AC-3 (pageSize cap)** — `pageSize: 200` → `TOOL_INPUT_INVALID` with a message naming the cap (100). `pageSize: 0` or negative → `TOOL_INPUT_INVALID`. Default `pageSize: 20` when not specified.
4. **AC-4 (empty result)** — Zero TwinClasses in domain → response is NOT an error. Markdown summary says "no TwinClasses match"; `structuredContent.classes` is `[]` (NOT null — architecture §"Null handling" line 373-375).
5. **AC-5 (permission denied)** — Twins returns HTTP 403 → envelope `code = TWINS_PERMISSION_DENIED`, message names the likely missing permission (`TWIN_CLASS_VIEW` — verify the actual permission string in the twins source for the search endpoint).
6. **AC-6 (stdio end-to-end)** — A real MCP client connected via `claude mcp add` (stdio transport) sends JSON-RPC `tools/call` for `list_classes` → response delivered on stdout. NO logs or extraneous output on stdout. Proves FR-TM-022 stdio transport.
7. **AC-7 (latency budget)** — Round-trip p95 ≤ 2 s with warm REST connection and ≤ 100 TwinClasses (NFR-TM-001). Measured via an integration test (Story 1.8 harness) OR a manual benchmark.
8. **AC-8 (fixture-based unit test)** — A unit test for `ListClassesTool` and `TwinClassSearchEndpoint` uses fixture JSON from `src/test/resources/fixtures/twin_class_search_response.json` (mocked RestClient). No live network in unit tests.

## Tasks / Subtasks

- [ ] **T1 — TwinClassSearchEndpoint** (AC-1, AC-8)
  - [ ] T1.1 Create `client/endpoint/TwinClassSearchEndpoint.java` — Spring component wrapping one twins resource
  - [ ] T1.2 Method `TwinClassSearchRsDTOv2 search(TwinClassSearchRqDTOv2 request)` — calls `twinsRestClient.post().uri("/private/twin_class/search/v2").body(request).retrieve().body(TwinClassSearchRsDTOv2.class)`
  - [ ] T1.3 Verify DTO class names from `twins-core-dto:1.4.191` (likely `TwinClassSearchRqDTOv2` / `TwinClassSearchRsDTOv2` — verify by reading the JAR via Context7 MCP or IDE inspection; do NOT guess the FQCN)
  - [ ] T1.4 Map HTTP errors: 403 → `TwinsPermissionDenied`, 404 → `TwinsNotFound`, 5xx after retry → `TwinsUnavailable` (handled by Story 1.4's Resilience4j config; here just translate status codes)
- [ ] **T2 — ListClassesTool** (AC-1-AC-5, AC-8)
  - [ ] T2.1 Create `tool/catalog/ListClassesTool.java` — Spring `@Component`, implements `TwinsMcpTool<ListClassesArgs>`
  - [ ] T2.2 Define `public record ListClassesArgs(String query, Integer pageSize, String cursor)` with JSR-380: `@Size(max=200) String query`, `@Min(1) @Max(100) Integer pageSize` (default 20 if null at framework layer), `@Size(max=200) String cursor`
  - [ ] T2.3 `key()` returns `ToolKey.LIST_CLASSES`; `wireName()` returns `"list_classes"`
  - [ ] T2.4 `call()`:
    1. Decode cursor (if present) — verify filter-hash matches `query` arg via `CursorCodec`
    2. Build `TwinClassSearchRqDTOv2` with `query`, `page` (from cursor or 0), `pageSize`
    3. Call `TwinClassSearchEndpoint.search(...)`
    4. Map results to markdown table via `MarkdownSummariser`
    5. Map results to `structuredContent` JSON (DTO fields verbatim; top-level keys snake_case)
    6. Compute `nextCursor` if more pages exist
    7. Build via `ToolResponseBuilder`
- [ ] **T3 — Fixtures** (AC-8)
  - [ ] T3.1 Create `src/test/resources/fixtures/twin_class_search_response.json` — representative response payload (capture from real twins instance OR hand-craft from DTO shape). Include ≥ 5 TwinClasses, enough to test pagination at small pageSize
  - [ ] T3.2 Create `src/test/resources/application-test.yml` — point RestClient at `MockRestServiceServer` or `MockWebServer` URL
- [ ] **T4 — Wire into ToolRegistry** (cross-cutting)
  - [ ] T4.1 Verify `ListClassesTool` is discovered by `ToolRegistry` (Story 1.5) and passes `ToolAllowlist` (its key is `LIST_CLASSES`, which Story 1.5 placed in the enum)
  - [ ] T4.2 Confirm the Spring AI MCP server exposes it via `tools/list` JSON-RPC method
- [ ] **T5 — Tests** (all ACs)
  - [ ] T5.1 `TwinClassSearchEndpointTest` — uses `MockRestServiceServer` to stub the twins response; asserts request body shape and response parsing
  - [ ] T5.2 `ListClassesToolTest` — happy path (markdown table + JSON shape), empty result, pageSize cap, cursor round-trip, filter-hash mismatch
  - [ ] T5.3 Permission-denied path: stub returns 403 → tool throws `TwinsPermissionDenied` → mapper produces `TWINS_PERMISSION_DENIED` envelope
  - [ ] T5.4 Boundary: response exactly at 32 KiB passes; response 1 byte over triggers pagination (verify `nextCursor` set, partial data returned)
- [ ] **T6 — Stdio smoke** (AC-6)
  - [ ] T6.1 Manual smoke: build the JAR, run with env vars set, pipe JSON-RPC `tools/list` then `tools/call list_classes` to stdin via `claude mcp add` (or direct stdin/stdout for a low-level smoke)
  - [ ] T6.2 Capture stdout, verify JSON-RPC response with `structuredContent` containing classes; verify NO log lines on stdout (everything went to stderr)
  - [ ] T6.3 Document the smoke command in a README stub (full README is Epic 4 / ARCH-SETUP-17)

## Dev Notes

### Architecture patterns and constraints

- **Domain-oriented tool surface** (D10): `list_classes` is one tool, NOT a wrapper per endpoint. It internally composes one REST call here (will be multiple in `describe_class`).
- **Prefer search endpoints over view-by-id** (D28): `POST /private/twin_class/search/v2` is the right call even when fetching a single class — server-side filtering + pagination support.
- **Hybrid response shape** (D17, FR-TM-040): markdown summary on top (human-scannable) + structuredContent JSON below (agent-parsable).
- **Opaque pagination cursor** (ARCH-13): base64-encoded JSON `{page, pageSize, filterHash}`. Filter-hash mismatch → `TOOL_INPUT_INVALID` to prevent inconsistent pages.
- **32 KiB response cap** (ARCH-16): over-cap → auto-paginate, NOT silent truncation.
- **structuredContent casing** (architecture §"Format Patterns" line 366-371): top-level keys snake_case; twins DTO fields pass-through verbatim.
- **Empty collections: `[]` never `null`** (architecture §"Null handling" line 373-375).
- **`/private/twin_class/search/v1` is `@Deprecated`** (D24) — MUST use `/v2`. CI no-deprecated-endpoints gate (ARCH-20) verifies this in Epic 4.
- **Permission on the twins side**: `@ProtectedBy(TWIN_CLASS_VIEW)` likely. Verify in twins source (`TwinClassSearchController.java` in `vendor/twins/`). The twins-mcp tool does NOT enforce permissions — twins does, server-side, via the M2M account's grants (D12).

### Source tree components to touch

- `client/endpoint/TwinClassSearchEndpoint.java`
- `tool/catalog/ListClassesTool.java` (with nested or sibling `ListClassesArgs`, `ListClassesResult` records per architecture §"Tool file organisation" line 358-360)
- `src/test/resources/fixtures/twin_class_search_response.json`
- `src/test/resources/application-test.yml`
- Tests in `src/test/java/org/twins/mcp/client/endpoint/` and `src/test/java/org/twins/mcp/tool/catalog/`

### Testing standards summary

- Unit tests with `MockRestServiceServer` (Spring Test) or `okhttp3.mockwebserver.MockWebServer` — no live twins in unit tests.
- Story 1.8's Testcontainers harness adds an integration test against real twins.
- For latency (AC-7): the integration test in Story 1.8 measures p95 with a warm REST connection. Story 1.7's scope is correctness, not latency.

### Library/framework requirements

| Component | Version | Source |
|---|---|---|
| `twins-core-dto` | 1.4.191 | `TwinClassSearchRqDTOv2`, `TwinClassSearchRsDTOv2` (verify exact names) |
| Spring AI MCP starter | via BOM | tool dispatch |
| `spring-boot-starter-test` | Boot 4.1 | `MockRestServiceServer` |

No new deps beyond.

### File structure requirements

- `ListClassesTool.java` contains the tool class + its `Args` record + its `Result` record (sibling records per architecture §"Tool file organisation" line 358-360; records can be top-level in same file).
- Fixtures live under `src/test/resources/fixtures/` — naming convention `<endpoint>_response.json`.
- `application-test.yml` overrides `twins.connection.base-url` to point at the mock server.

## Project Structure Notes

- Aligns with architecture §485-611 (`client/endpoint/TwinClassSearchEndpoint.java`, `tool/catalog/ListClassesTool.java`).
- This is the **integration story** for Epic 1: every previous story's component is exercised end-to-end. If something doesn't fit (an interface mismatch, a missing hook), surface it BEFORE working around it — adjust the upstream story's file or ADR.

## References

- Architecture:
  - §"API & Communication" line 245-269 (composite-tool, error envelope integration, cursor, hybrid builder, size cap, Resilience4j)
  - §"Implementation Patterns" line 322-484 (naming, structure, format — critical for this story)
  - §"Complete Project Directory Structure" line 543-547 (`client/endpoint/` and `tool/catalog/` packages)
  - §"Requirements to Structure Mapping" line 643-644 (FR-TM-001 → file mapping)
- Decision log: D10 (domain-oriented tool), D17 (hybrid response), D24 (`/v2` not `/v1`), D28 (prefer search), D30 (glossary — not this story but adjacent)
- PRD: FR-TM-001 (list_classes), FR-TM-022 (stdio transport), FR-TM-040 (hybrid shape), FR-TM-041 (pagination), NFR-TM-001 (p95 ≤ 2s for list_classes)
- twins source: `vendor/twins/` — `TwinClassSearchController.java`, request/response DTOs, `@ProtectedBy` annotation to verify permission name
- Epics: `_bmad-output/planning-artifacts/epics.md` §"Story 1.7"
- Memory: `[[twins-core-dto-maven-artifact]]` (DTO source), `[[twins-rest-headers]]` (header transport convention)

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
