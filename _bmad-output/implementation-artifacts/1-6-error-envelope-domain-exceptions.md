# Story 1.6: Error Envelope & Domain Exceptions

Status: ready-for-dev

## Story

As an **MCP client (e.g., Claude)**,
I want **consistent, sanitised error envelopes regardless of the underlying failure mode**,
so that **I can reliably surface errors to the user without leaking secrets, stack traces, or twins-internal details**.

## Acceptance Criteria

1. **AC-1 (envelope shape)** — Every tool failure produces a JSON error envelope with: `code` (one of `TWINS_PERMISSION_DENIED`, `TWINS_NOT_FOUND`, `TWINS_UNAVAILABLE`, `TOOL_INPUT_INVALID`, `INTERNAL_ERROR`), `message` (human-readable, sanitised), optional `twinsErrorCode` (original twins error code when available), optional `domainId` (the configured DomainId value, NOT a secret).
2. **AC-2 (permission denied)** — HTTP 403 from twins (or a tool throwing `TwinsPermissionDenied`) → envelope `code = TWINS_PERMISSION_DENIED`. The twins 403 response body is NOT echoed; only a generic "the M2M account may lack the required permission" message.
3. **AC-3 (not found)** — HTTP 404 (or `TwinsNotFound`) → `code = TWINS_NOT_FOUND`. Message identifies which resource class was queried (e.g., "TwinClass not found").
4. **AC-4 (unavailable)** — Network errors, HTTP 5xx after Resilience4j exhaustion, or `TwinsUnavailable` → `code = TWINS_UNAVAILABLE`. Message names the operation attempted (e.g., "list_classes").
5. **AC-5 (validation)** — JSR-380 violations caught from `ToolArgsValidator` (Story 1.5) → `code = TOOL_INPUT_INVALID`. Message lists each violation (`field`, `violation`).
6. **AC-6 (catch-all)** — Any other uncaught exception → `code = INTERNAL_ERROR`, message "An internal error occurred". Full stack trace is logged at ERROR to stderr (sanitised by Story 1.3) but NOT included in the envelope.
7. **AC-7 (no leak in error path)** — The `AuthToken` value, `TWINS_M2M_CLIENT_SECRET` value, and any token-shaped string (JWT, base64 blob ≥ 32 chars) NEVER appear in any envelope field. Verified by a unit test that constructs exceptions containing these values and asserts the envelope's `message` field.
8. **AC-8 (no stack traces escape)** — Stack traces are NOT serialised into the envelope. The `Throwable.getMessage()` is run through the secrets sanitiser (Story 1.3 hook) before being placed in the envelope's `message` field.

## Tasks / Subtasks

- [ ] **T1 — Domain exception hierarchy** (AC-2, AC-3, AC-4, AC-5)
  - [ ] T1.1 Create `error/ToolException.java` — base class extends `RuntimeException`; carries optional `twinsErrorCode` and `domainId`
  - [ ] T1.2 Create `error/TwinsPermissionDenied.java extends ToolException`
  - [ ] T1.3 Create `error/TwinsNotFound.java extends ToolException` — constructor accepts the resource class name (e.g., "TwinClass")
  - [ ] T1.4 Create `error/TwinsUnavailable.java extends ToolException` — constructor accepts the operation name (e.g., "list_classes")
  - [ ] T1.5 Create `error/ToolInputInvalid.java extends ToolException` — constructor accepts a list of `{field, violation}` records
  - [ ] T1.6 Refactor Story 1.4's placeholder exceptions (`M2MAuthException`, `RestCallException`) to use these final types
- [ ] **T2 — ErrorEnvelope record** (AC-1)
  - [ ] T2.1 Create `error/ErrorEnvelope.java` — record with `code` (enum `ErrorCode`), `message`, `twinsErrorCode` (nullable), `domainId` (nullable)
  - [ ] T2.2 Create `error/ErrorCode.java` enum — the five codes from AC-1
  - [ ] T2.3 Jackson annotations: `@JsonInclude(NON_NULL)` on the record to omit null fields (architecture §"Null handling" line 373-375)
- [ ] **T3 — ErrorEnvelopeMapper** (AC-2-AC-8)
  - [ ] T3.1 Create `error/ErrorEnvelopeMapper.java` — Spring component
  - [ ] T3.2 Method `ErrorEnvelope map(Throwable t, String operationName)` — dispatches by exception type:
    - `TwinsPermissionDenied` → `ErrorCode.TWINS_PERMISSION_DENIED`
    - `TwinsNotFound` → `ErrorCode.TWINS_NOT_FOUND`
    - `TwinsUnavailable` → `ErrorCode.TWINS_UNAVAILABLE`
    - `ToolInputInvalid` → `ErrorCode.TOOL_INPUT_INVALID`
    - any other → `ErrorCode.INTERNAL_ERROR`
  - [ ] T3.3 Run `Throwable.getMessage()` through `SecretsSanitiser.sanitise(String)` before placing in the envelope (use the static accessor from Story 1.3)
  - [ ] T3.4 Log full stack trace at ERROR level on INTERNAL_ERROR; WARN on TwinsPermissionDenied / TwinsNotFound; ERROR on TwinsUnavailable
  - [ ] T3.5 Never echo HTTP response bodies from twins into the envelope — those are read for diagnostic purposes only and discarded
- [ ] **T4 — Wire into MCP tool dispatch** (AC-1)
  - [ ] T4.1 Hook the mapper into the tool-dispatch pipeline (verify the Spring AI MCP `ToolCallback` extension point via Context7 MCP at impl time)
  - [ ] T4.2 Catch all exceptions at the dispatcher boundary; never let a `Throwable` reach the MCP framework unhandled
  - [ ] T4.3 The envelope is returned as the tool's structuredContent with an appropriate error indicator (MCP spec — likely `isError: true` on the response)
- [ ] **T5 — Tests** (all ACs)
  - [ ] T5.1 `ErrorEnvelopeMapperTest` — one test per AC-2..AC-6 (parameterised)
  - [ ] T5.2 Same — AC-7: exception message contains `SECRETVAL` literal → envelope `message` does NOT contain `SECRETVAL`
  - [ ] T5.3 Same — AC-8: stack trace is logged (use a Logback test appender) but NOT present in the envelope's serialised JSON

## Dev Notes

### Architecture patterns and constraints

- **Tools throw domain exceptions** (architecture §"Error throwing" line 408-411): tools NEVER throw raw `RuntimeException` or `IOException`. They throw `TwinsPermissionDenied`, `TwinsNotFound`, `TwinsUnavailable`, `ToolInputInvalid`. Tools never catch-and-swallow — let it propagate to the mapper.
- **Error envelope is the only error shape** (ARCH-12): the JSON shape is fixed. The MCP client sees one consistent contract.
- **HTTP-to-envelope mapping** (ARCH-12): HTTP 403 → PERMISSION_DENIED; 404 → NOT_FOUND; 5xx → TWINS_UNAVAILABLE; other 4xx → TWINS_ERROR (note: AC-1 lists five codes; the spec's "TWINS_ERROR" is folded into INTERNAL_ERROR or TOOL_INPUT_INVALID depending on context — pick one and document).
- **Stack traces never escape the process** (ARCH-12): the envelope contains `message` only. Full traces go to logs (sanitised).
- **Sanitiser runs on exception messages** (NFR-TM-003, AC-7): the mapper MUST pipe `Throwable.getMessage()` through `SecretsSanitiser` before serialisation. This is the second line of defense (after Logback).
- **No twins response body in envelope** (AC-2, AC-5): the body MAY contain useful diagnostics but also MAY contain secrets. Reading it is OK; echoing it is NOT.

### Source tree components to touch

- `error/ToolException.java` (base class)
- `error/TwinsPermissionDenied.java`
- `error/TwinsNotFound.java`
- `error/TwinsUnavailable.java`
- `error/ToolInputInvalid.java`
- `error/ErrorEnvelope.java`
- `error/ErrorCode.java`
- `error/ErrorEnvelopeMapper.java`
- Tests under `src/test/java/org/twins/mcp/error/`

### Testing standards summary

- Parameterised JUnit 5 tests for the five exception → code mappings (T5.1).
- Use `assertj` or `JUnit` assertions on the envelope's serialised JSON to verify no secret leak (T5.2).
- For stack-trace-not-in-envelope (T5.3): serialise via `ObjectMapper.writeValueAsString(envelope)` and assert no `"\tat org.twins..."` stack frame appears.

### Library/framework requirements

| Component | Version | Source |
|---|---|---|
| Jackson | Boot 4.1 default | for envelope JSON serialisation |
| `jakarta.validation` | Story 1.2 dep | for `ToolInputInvalid` carrying violation metadata |
| SLF4J/Logback | Boot 4.1 default | for stack trace logging |

No new deps.

### File structure requirements

- All classes in `org.twins.mcp.error`.
- `ErrorEnvelope` is a Java record (immutable).
- `ErrorCode` is an enum with `String wireCode()` (e.g., `"TWINS_PERMISSION_DENIED"`) for JSON.
- `ErrorEnvelopeMapper` is a Spring `@Component` so it can be injected into the tool-dispatch pipeline.

## Project Structure Notes

- Aligns with architecture §485-611 (`error/ToolException.java`, `error/TwinsPermissionDenied.java`, etc.).
- Refactor Story 1.4's placeholder exceptions to use these final types. The contract between Stories 1.4 and 1.6 is: 1.4 throws placeholders, 1.6 introduces the real types and migrates.

## References

- Architecture:
  - §"API & Communication" line 247-258 (ARCH-12 error envelope, HTTP-to-code mapping)
  - §"Process Patterns" line 408-411 (error throwing — domain exceptions only)
  - §"Format Patterns" line 373-378 (null handling, date/time — applies to envelope fields)
  - §"Communication Patterns" line 392-400 (log levels — ERROR/WARN/INFO/DEBUG/TRACE)
- Decision log: D35 (sanitiser covers "Bearer" terminology only — relevant for envelope message patterns)
- PRD: NFR-TM-002 (graceful degradation), NFR-TM-003 (secrets), FR-TM-042 (output sanitisation)
- Epics: `_bmad-output/planning-artifacts/epics.md` §"Story 1.6"

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
