# Story 1.5: Tool Foundation — Registry, Allowlist, Validator, Response Pipeline

Status: done

## Story

As a **future tool author** (in Epics 2/3),
I want **shared infrastructure for tool registration, input validation, and hybrid response shaping**,
so that **adding a new tool is a small, well-defined task and every tool response has the same shape, size cap, and pagination contract**.

## Acceptance Criteria

1. **AC-1 (tool discovery)** — A Spring bean implementing `TwinsMcpTool` whose `key()` returns a value in the `ToolKey` enum is auto-registered by `ToolRegistry` at startup. Non-allowlisted keys cause startup failure (AC-2).
2. **AC-2 (allowlist enforcement)** — A bean implementing `TwinsMcpTool` whose `key()` is NOT in the `ToolKey` enum causes startup to FAIL FAST with a clear error naming the offending bean's class name and its returned key. This is the read-only-surface gate (ARCH-9).
3. **AC-3 (args validation)** — Tool invocation runs JSR-380 validation on the `Args` record BEFORE `call()` is invoked. Constraint violations result in `ToolInputInvalid` (mapped by Story 1.6) — never reach `call()`, never become a generic 500.
4. **AC-4 (hybrid response)** — `ToolResponseBuilder` produces a response with BOTH: (a) markdown summary (5–30 lines, ≤ 30% of the structured JSON size), (b) `structuredContent` JSON matching twins DTO field names (snake_case top-level keys; twins DTO fields verbatim).
5. **AC-5 (size cap + pagination)** — Total response size ≤ 32 KiB. If the rendered response would exceed, pagination kicks in: return what fits + an opaque `nextCursor`. The builder NEVER silently truncates data.
6. **AC-6 (cursor)** — `CursorCodec` produces base64-encoded JSON `{page, pageSize, filterHash}` where `filterHash` is SHA-256 of the request filter. On the next call, if the incoming filter's hash ≠ cursor's hash → `ToolInputInvalid` (prevents inconsistent pages when the agent changes filter mid-pagination).
7. **AC-7 (pageSize constraints)** — Tools accepting `pageSize` argument: max 100, default 20. `pageSize > 100` → `ToolInputInvalid`. `pageSize ≤ 0` → `ToolInputInvalid`.
8. **AC-8 (MCP server bean)** — `McpServerConfig` registers the Spring AI MCP server bean via `spring-ai-starter-mcp-server` for stdio transport. JSON-RPC requests from stdin are dispatched to registered tools; responses are written to stdout.

## Tasks / Subtasks

- [ ] **T1 — TwinsMcpTool interface + ToolKey enum** (AC-1, AC-2)
  - [ ] T1.1 Create `tool/TwinsMcpTool.java` — interface with `ToolKey key()`, `String description()`, `Class<?> argsType()`, `ToolResponse call(Object args)` (or genericised: `TwinsMcpTool<A>` with `call(A args)`). Verify the Spring AI MCP starter's expected tool interface at impl time; we MAY extend/bridge it rather than reinvent
  - [ ] T1.2 Create `tool/ToolKey.java` enum — initially contains `LIST_CLASSES` only (added in Story 1.7). Story 1.5 leaves the enum with one entry as a placeholder; Epics 2/3 will add `DESCRIBE_CLASS`, `DESCRIBE_CLASS_RELATIONS`, `DESCRIBE_CLASS_STATUSES`, `LIST_GLOSSARY_SECTIONS`, `GET_GLOSSARY_SECTION`, `GET_GLOSSARY_TERM`
- [ ] **T2 — ToolAllowlist** (AC-2)
  - [ ] T2.1 Create `tool/ToolAllowlist.java` — Spring bean that on startup iterates all `TwinsMcpTool` beans (via `ApplicationContext.getBeansOfType()`) and verifies each `key()` is in `ToolKey`
  - [ ] T2.2 On violation: throw a fatal `IllegalStateException` from a `@PostConstruct` or `ApplicationListener<ContextRefreshedEvent>` so Spring fails to start
  - [ ] T2.3 Error message names the bean class + the offending key value
- [ ] **T3 — ToolRegistry** (AC-1)
  - [ ] T3.1 Create `tool/ToolRegistry.java` — Spring bean, builds a `Map<ToolKey, TwinsMcpTool<?>>` from all beans
  - [ ] T3.2 Exposes `lookup(ToolKey)` for the MCP server dispatcher (in Story 1.7)
- [ ] **T4 — ToolArgsValidator** (AC-3)
  - [ ] T4.1 Create `tool/ToolArgsValidator.java` — wrapper around `jakarta.validation.Validator`
  - [ ] T4.2 Method `<A> void validate(A args)` — runs JSR-380; throws `ToolInputInvalid` (placeholder class until Story 1.6) on violations
  - [ ] T4.3 Hook into the MCP server's tool-dispatch pipeline (a Spring AI MCP `ToolCallback` or analogous — verify at impl time). Every `call()` flows through this validator first
- [ ] **T5 — ToolResponse + ToolResponseBuilder** (AC-4, AC-5)
  - [ ] T5.1 Create `response/ToolResponse.java` — immutable record with `markdown`, `structuredContent` (JsonNode or `Map<String,Object>`), `nextCursor` (optional)
  - [ ] T5.2 Create `response/ToolResponseBuilder.java` — fluent builder: `withMarkdown(...)`, `withStructuredContent(...)`, `withPagination(nextCursor)`, `build()`
  - [ ] T5.3 Builder applies `SizeCapEnforcer` on `build()` — if size > 32 KiB and no cursor provided, throw `IllegalStateException` (programmer error — pagination should have been applied by the tool)
- [ ] **T6 — CursorCodec** (AC-6)
  - [ ] T6.1 Create `response/CursorCodec.java` — `String encode(int page, int pageSize, String filterJson)` and `Cursor decode(String cursor)`
  - [ ] T6.2 Encode: build JSON `{"page":N, "pageSize":N, "filterHash":"<sha256-hex>"}`, base64-encode
  - [ ] T6.3 Decode: reverse — base64-decode, parse JSON, return record
  - [ ] T6.4 `verifyFilter(Cursor cursor, String currentFilterJson)` — throws `ToolInputInvalid` if hashes mismatch
- [ ] **T7 — MarkdownSummariser + SizeCapEnforcer** (AC-4, AC-5)
  - [ ] T7.1 Create `response/MarkdownSummariser.java` — helpers for building CommonMark tables from lists of DTOs (uses the architecture §"Format Patterns" guidance)
  - [ ] T7.2 Create `response/SizeCapEnforcer.java` — `enforce(ToolResponse response)` measures total JSON+markdown size; throws `IllegalStateException` if > 32 KiB
- [ ] **T8 — McpServerConfig** (AC-8)
  - [ ] T8.1 Create `config/McpServerConfig.java` — `@Configuration` that customises the Spring AI MCP server bean
  - [ ] T8.2 Verify the exact auto-config hook in `spring-ai-starter-mcp-server` for stdio transport (use Context7 MCP per CLAUDE.md rule). The starter likely exposes `McpServerProperties` or similar
  - [ ] T8.3 Confirm stdio mode: no HTTP listener, JSON-RPC over stdin/stdout
- [ ] **T9 — Tests** (all ACs)
  - [ ] T9.1 `ToolAllowlistTest` — happy path + non-allowlisted bean causes startup failure (use `ApplicationContextRunner` to verify context fails)
  - [ ] T9.2 `ToolArgsValidatorTest` — JSR-380 violations throw the right exception type
  - [ ] T9.3 `ToolResponseBuilderTest` — markdown + JSON present, size cap enforced
  - [ ] T9.4 `CursorCodecTest` — round-trip; filterHash mismatch throws
  - [ ] T9.5 `SizeCapEnforcerTest` — boundary cases at 32 KiB

## Dev Notes

### Architecture patterns and constraints

- **Composite-tool pattern** (ARCH-11): each tool is a Spring bean implementing `TwinsMcpTool`; tools freely compose N REST calls. The interface should NOT expose endpoint-bound methods.
- **One file per tool** (architecture §"Tool file organisation" line 358-360): each tool class + its `Args` + `Result` records live in one file (records can be siblings in the same package, OR nested). Goal: parallel agent work — two tools, two files, zero merge conflicts.
- **JSR-380 validation at framework layer** (architecture §"Tool args validation" line 404-406): tools never hand-validate. Annotations: `@NotBlank`, `@Size(max=...)`, `@Pattern(...)`, `@Min`, `@Max`.
- **Read-only surface enforcement** (ARCH-9): allowlist at startup is non-negotiable. A mutating tool that's not in `ToolKey` CANNOT ship.
- **Pagination cursor** (ARCH-13): opaque base64-encoded JSON `{page, pageSize, filterHash}`. The `filterHash` is critical — it prevents an agent from passing a cursor that was issued for a different filter.
- **32 KiB response cap** (ARCH-16, OQ-PRD-2 resolved): over-cap → auto-paginate. The tool is responsible for paginating BEFORE calling `build()`; the builder enforces as a safety net.
- **structuredContent JSON casing** (architecture §"Format Patterns" line 366-371): top-level keys snake_case; twins DTO field names pass-through verbatim (do NOT transliterate).
- **Markdown summary rules** (architecture §"Markdown summary" line 381-385): CommonMark (no GFM extensions EXCEPT for entity-list tables, where GFM tables are accepted). List of entities → table. Single entity → bold-key block. Always include tool name + pagination hint when list is partial.
- **Spring AI MCP integration**: verify the actual `ToolCallback` / tool-dispatch contract in `spring-ai-starter-mcp-server` 2.0.x at impl time via Context7 MCP (per CLAUDE.md rule). Do NOT assume the API surface from memory.

### Source tree components to touch

- `tool/TwinsMcpTool.java` (interface)
- `tool/ToolKey.java` (enum)
- `tool/ToolAllowlist.java`
- `tool/ToolRegistry.java`
- `tool/ToolArgsValidator.java`
- `response/ToolResponse.java`
- `response/ToolResponseBuilder.java`
- `response/CursorCodec.java`
- `response/MarkdownSummariser.java`
- `response/SizeCapEnforcer.java`
- `config/McpServerConfig.java`
- Tests under `src/test/java/org/twins/mcp/tool/` and `src/test/java/org/twins/mcp/response/`

### Testing standards summary

- `ApplicationContextRunner` (Spring Boot Test) for the allowlist startup-failure test (T9.1).
- Plain JUnit for `CursorCodec` / `SizeCapEnforcer` (no Spring context needed).
- Story 1.7 will add the first end-to-end tool test using this infrastructure.

### Library/framework requirements

| Component | Version | Source |
|---|---|---|
| Spring AI MCP server starter | via BOM 2.0.0 | FR-TM-061 |
| Jackson (`JsonNode`) | Boot 4.1 default | transitive |
| `jakarta.validation` (`spring-boot-starter-validation`) | Boot 4.1 default | added in Story 1.2 |

### File structure requirements

- All tool classes in `org.twins.mcp.tool` and `org.twins.mcp.response`.
- `ToolKey` enum entries in `UPPER_SNAKE_CASE` (e.g., `LIST_CLASSES`); the wire name in MCP is `lower_snake_case` (`list_classes`) — provide a `wireName()` method on the enum if helpful.
- Builder pattern for `ToolResponseBuilder`: fluent, immutable on `build()`.
- `CursorCodec` is stateless — make it a final class with static methods OR a Spring bean (latter preferred for testability).

## Project Structure Notes

- Aligns with architecture §485-611 (the entire `tool/` and `response/` package skeleton).
- This story DOES NOT implement any concrete catalog or glossary tool — those land in Story 1.7 (`list_classes`) and Epics 2/3.
- The `ToolKey` enum starts with only `LIST_CLASSES` (placeholder); later stories/epics append.

## References

- Architecture:
  - §"API & Communication" line 245-266 (ARCH-11 composite-tool, ARCH-12 error envelope integration, ARCH-13 cursor, ARCH-14 hybrid response builder, ARCH-16 size cap)
  - §"Authentication & Security" line 239-240 (ARCH-9 read-only allowlist)
  - §"Implementation Patterns" line 322-484 (naming, structure, format, process, enforcement — critical for this story)
- Decision log: D10 (domain-oriented tool surface — tool ≠ endpoint), D17 (hybrid response shape), D28 (prefer search endpoints)
- PRD: FR-TM-040 (hybrid response), FR-TM-041 (pagination), FR-TM-061 (Spring AI starter)
- Epics: `_bmad-output/planning-artifacts/epics.md` §"Story 1.5"

## Dev Agent Record

### Agent Model Used

Claude (twins-mcp / GLM-5.2)

### Debug Log References

- `./gradlew compileJava` — BUILD SUCCESSFUL (no deprecation warnings; MCP SDK 2.0 + Spring AI 2.0 API verified via `javap`).
- `./gradlew test` — BUILD SUCCESSFUL, **108 tests, 0 failures, 0 skipped** (was 78; +30 for Story 1.5):
  - ToolRegistryAllowlistTest: 5 · ToolArgsValidatorTest: 7 · ToolJsonSchemaGeneratorTest: 4 · CursorCodecTest: 6 · SizeCapEnforcerTest: 4 · ToolResponseBuilderTest: 4.
- `./gradlew build` — BUILD SUCCESSFUL (bootJar produced).
- `./gradlew bootRun </dev/null` (env vars set, dummy twins URL) — exit 0: "Started Application in 19.9s", `McpServerAutoConfiguration` enabled tools/resources/prompts/completions, stdio transport wired, customizer bean created. Confirms MCP server boots over stdio with the bridge present.

### Completion Notes List

- **Programmatic MCP bridge via `McpSyncServerCustomizer`, not `@McpTool` annotations (AC-8).** The annotation scanner would let a stray `@McpTool` register with MCP bypassing the `ToolKey` allowlist. Instead `McpServerConfig` exposes a single `McpSyncServerCustomizer` that builds one `McpServerFeatures.SyncToolSpecification` per registered `TwinsMcpTool` (tool name = `ToolKey.wireName()`, input schema from `ToolJsonSchemaGenerator`, call handler = deserialise → `ToolArgsValidator.validate` → `call` → `ToolResponse` → `CallToolResult`). Verified the auto-config consumes it: `McpServerAutoConfiguration.mcpSyncServer(...)` declares `Optional<McpSyncServerCustomizer>` and applies it via `lambda$mcpSyncServer$2(spec, customizer)`. `spring-ai-starter-mcp-server` builds the `McpSyncServer` + `StdioServerTransportProvider` itself; `application.yml` sets `spring.ai.mcp.server.{stdio=true, type=SYNC, name, version}`.
- **Jackson 3 (Boot 4.1), not Jackson 2.** Boot 4.1 ships `tools.jackson.core:jackson-databind:3.1.4` (package `tools.jackson.*`); `com.fasterxml.jackson` is absent. `CursorCodec`, `ToolResponseBuilder`, and `McpServerConfig` use `tools.jackson.databind.ObjectMapper`; `JacksonException` is unchecked (`extends RuntimeException`) — caught explicitly where needed. `convertValue(map, Class)` throws `IllegalArgumentException` on malformed input.
- **JSON schema reads JSR-380 from record fields, not `RecordComponent` (AC-3 wiring).** Jakarta validation annotations have no `RECORD_COMPONENT` target, so the compiler records them on the backing field/accessor; `RecordComponent.getAnnotation(...)` returns null. `ToolJsonSchemaGenerator` reads `argsType.getDeclaredField(name).getAnnotation(...)`. Translates `@NotBlank/@NotNull/@NotEmpty` → required, `@Size` → minLength/maxLength, `@Min/@Max` → minimum/maximum, `@Pattern` → pattern.
- **AC-2 read-only gate (ARCH-9).** `key()` is typed to `ToolKey`, so a value outside the enum is structurally impossible at compile time; the runtime `ToolAllowlist` is defense-in-depth — it fails fast on a null key or a key outside the active allowed set (default `EnumSet.allOf(ToolKey)`, overridable via a package-private constructor for tests). `ToolRegistry` additionally rejects duplicate keys.
- **Page-size constraints (AC-7)** are JSR-380 facets on each tool's `Args` (`@Min(1) @Max(100) Integer pageSize`), enforced by the single `ToolArgsValidator` hook — verified with `pageSize` 0/200/null.
- **32 KiB cap (AC-5)** enforced hard by `SizeCapEnforcer` inside `ToolResponseBuilder.build()` — an over-cap response throws `IllegalStateException` (never silently truncated), regardless of cursor. The markdown "≤30 % of structured JSON / 5–30 lines" rule (AC-4) is a `MarkdownSummariser` authoring guideline, not a builder assertion (only the hard byte cap is asserted).
- **Cursor (AC-6)**: base64url JSON `{page, pageSize, filterHash=SHA-256(filter)}`; `verifyFilter` rejects a cursor replayed against a changed filter with `ToolInputInvalid`.
- **No full `@SpringBootTest`.** MCP wiring verified via `bootRun` smoke (server boots, capabilities enabled) + `javap` of the auto-config (customizer is consumed). End-to-end dispatch (a real tool over stdio) is validated in Story 1.7 (`list_classes`) and Story 1.8 (Testcontainers IT).

### File List

- `src/main/resources/application.yml` (modified — `spring.ai.mcp.server.{stdio,type,name,version}`)
- `src/main/java/org/twins/mcp/tool/ToolKey.java` (new — allowlist enum)
- `src/main/java/org/twins/mcp/tool/TwinsMcpTool.java` (new — domain tool interface)
- `src/main/java/org/twins/mcp/tool/ToolRegistry.java` (new — AC-1 discovery)
- `src/main/java/org/twins/mcp/tool/ToolAllowlist.java` (new — AC-2 gate)
- `src/main/java/org/twins/mcp/tool/ToolArgsValidator.java` (new — AC-3/AC-7)
- `src/main/java/org/twins/mcp/tool/ToolJsonSchemaGenerator.java` (new — Args → JSON schema)
- `src/main/java/org/twins/mcp/response/ToolResponse.java` (new — hybrid response record)
- `src/main/java/org/twins/mcp/response/ToolResponseBuilder.java` (new — AC-4/AC-5)
- `src/main/java/org/twins/mcp/response/CursorCodec.java` (new — AC-6)
- `src/main/java/org/twins/mcp/response/SizeCapEnforcer.java` (new — 32 KiB)
- `src/main/java/org/twins/mcp/response/MarkdownSummariser.java` (new — table/hint helpers)
- `src/main/java/org/twins/mcp/error/ToolInputInvalid.java` (new)
- `src/main/java/org/twins/mcp/config/McpServerConfig.java` (new — AC-8 bridge)
- `src/test/java/org/twins/mcp/tool/ToolRegistryAllowlistTest.java` (new — 5 tests)
- `src/test/java/org/twins/mcp/tool/ToolArgsValidatorTest.java` (new — 7 tests)
- `src/test/java/org/twins/mcp/tool/ToolJsonSchemaGeneratorTest.java` (new — 4 tests)
- `src/test/java/org/twins/mcp/response/CursorCodecTest.java` (new — 6 tests)
- `src/test/java/org/twins/mcp/response/SizeCapEnforcerTest.java` (new — 4 tests)
- `src/test/java/org/twins/mcp/response/ToolResponseBuilderTest.java` (new — 4 tests)

## Change Log

| Date | Change |
|---|---|
| 2026-07-01 | Story created from Epic 1 breakdown (bmad-create-story) |
| 2026-07-15 | Implementation: TwinsMcpTool + ToolKey + ToolRegistry + ToolAllowlist + ToolArgsValidator + ToolJsonSchemaGenerator + ToolResponse/Builder + CursorCodec + SizeCapEnforcer + MarkdownSummariser + ToolInputInvalid + McpServerConfig + 30 tests (108 total, 0 failures). Story 1.5 → done. MCP bridge via `McpSyncServerCustomizer` (programmatic, allowlist-authoritative); Jackson 3 (`tools.jackson`); MCP stdio verified via bootRun smoke + javap of the auto-config. |
