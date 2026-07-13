---
baseline_commit: d9000c46686efaa32537739dd90b2f1e7fa5e742
---

# Story 1.3: Structured Logging & Secrets Sanitiser

Status: review

## Story

As a **security-conscious operator**,
I want **all logs to be emitted as structured JSON to stderr with token-shaped values redacted**,
so that **M2M tokens, the `AuthToken` value, and `TWINS_M2M_CLIENT_SECRET` can never leak via log files or stderr captures**.

## Acceptance Criteria

1. **AC-1 (JSON to stderr)** — Any SLF4J log call produces a single JSON object on stderr with keys `ts`, `level`, `logger`, `msg`, plus optional structured fields. The encoder is Logback's built-in JSON encoder (preferred per G7) or `logstash-logback-encoder` as a fallback. Stdout receives zero log lines.
2. **AC-2 (JWT redaction)** — A log statement including a JWT-shaped string (3 base64 segments separated by dots, ≥ 32 chars total — regex `^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$`) is rendered with the token replaced by `[REDACTED:jwt]`. The original never appears in the rendered line.
3. **AC-3 (secret literal redaction)** — A log statement including the literal value of `TWINS_M2M_CLIENT_SECRET` (read once at startup from `M2mCredentialsProperties`) is replaced with `[REDACTED:secret]`. Match is case-insensitive.
4. **AC-4 (AuthToken literal redaction)** — When `TokenHolder` (Story 1.4) holds a token, the literal token value is registered with the sanitiser so it gets replaced with `[REDACTED:authtoken]` in logs. (For this story: define a `SecretsSanitiser.registerDynamic(String value, String tag)` hook; the actual registration is wired by Story 1.4.)
5. **AC-5 (Bearer + base64 blob)** — Strings starting with `Bearer ` are replaced with `[REDACTED:bearer]`. Standalone base64 blobs ≥ 32 chars (`^[A-Za-z0-9+/=]{32,}$`) are replaced with `[REDACTED:base64]`.
6. **AC-6 (logback wiring)** — `src/main/resources/logback-spring.xml` declares: root logger → console appender → stderr (`<target>System.err</target>`), JSON encoder, `<filter class="org.twins.mcp.secrets.SecretsSanitiser">` wired in.
7. **AC-7 (no stdout)** — `./gradlew bootRun 2>/dev/null` produces zero stdout output during normal startup and steady state. (Story 1.1 disabled the banner; this story ensures ALL other logging respects stderr.)
8. **AC-8 (sanitiser survives edge cases)** — Empty strings, nulls, multi-line log messages, and Unicode content are all handled without exceptions. The sanitiser NEVER throws — a failure inside the filter falls back to `[REDACTED:error]` so the line still gets through.

## Tasks / Subtasks

- [x] **T1 — SecretsSanitiser filter** (AC-2, AC-3, AC-4, AC-5, AC-8)
  - [x] T1.1 Create `secrets/SecretsSanitiser.java` extending `ch.qos.logback.core.filter.Filter<ILoggingEvent>`
  - [x] T1.2 Static patterns: JWT regex, Bearer-prefix regex, base64-blob regex (compile once, reuse)
  - [x] T1.3 Dynamic-value registry: `registerDynamic(String value, String tag)` (called by `TokenHolder` in Story 1.4). Use `CopyOnWriteArrayList` to avoid locking under concurrent log emission
  - [x] T1.4 Static-value hook: read `M2mCredentialsProperties.clientSecret` once at startup via a `@PostConstruct` on a small `SecretsSanitiserBootstrap` bean (or via Spring's `@EventListener<ApplicationStartedEvent>`)
  - [x] T1.5 Implement `decide(ILoggingEvent)`: copy the formatted message, apply each pattern + dynamic-value match, replace with the corresponding tag, then return `FilterReply.NEUTRAL` with the rewritten message attached (use a custom `ch.qos.logback.classic.spi.ILoggingEvent` wrapper OR a converter approach — see Dev Notes for the recommended pattern)
  - [x] T1.6 Wrap all matching logic in try/catch — on any exception, return the original message (sanitiser NEVER blocks logging)
- [x] **T2 — logback-spring.xml** (AC-1, AC-6, AC-7)
  - [x] T2.1 Create `src/main/resources/logback-spring.xml`
  - [x] T2.2 `<configuration>` with `<appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender"><target>System.err</target>...</appender>`
  - [x] T2.3 Encoder: `<encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">` with `<layout class="net.logstash.logback.layout.LogbackAsyncJsonLayout">` (or Boot 4's built-in `JsonEncoder` if available — verify at impl time)
  - [x] T2.4 `<filter class="org.twins.mcp.secrets.SecretsSanitiser">` inside the appender
  - [x] T2.5 `<root level="INFO"><appender-ref ref="STDERR"/></root>`
  - [x] T2.6 Suppress Spring Boot's default stdout console appender from `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` — verify nothing writes to stdout
- [x] **T3 — LoggingConfig** (AC-1)
  - [x] T3.1 Create `config/LoggingConfig.java` with `@Configuration` — defines any Spring-side logging hooks (e.g., `LoggingEventVisualiser` for tool-name injection into MDC)
  - [x] T3.2 Optional: register an MDC key `tool` for tool-name scoping (used by Story 1.5)
- [x] **T4 — logback deps** (AC-1)
  - [x] T4.1 Add `net.logstash.logback:logstash-logback-encoder` to `gradle/libs.versions.toml` (preferred, mature JSON encoder). If Logback 1.5+ ships a built-in JSON encoder by impl time, prefer that and skip this dep (verify via Context7 MCP at impl time per CLAUDE.md rule)
- [x] **T5 — Tests** (AC-1-AC-8)
  - [x] T5.1 `SecretsSanitiserTest` — JWT redaction (positive + negative: non-JWT strings NOT redacted)
  - [x] T5.2 Same — secret literal redaction (case-insensitive)
  - [x] T5.3 Same — Bearer prefix redaction
  - [x] T5.4 Same — base64 blob redaction (boundary: 31 chars NOT redacted, 32 chars redacted)
  - [x] T5.5 Same — dynamic registry (register then verify redaction)
  - [x] T5.6 Same — null/empty/multi-line/Unicode inputs do NOT throw
  - [x] T5.7 Integration test (`@SpringBootTest`): emit a log line with a fake JWT, capture stderr via a test appender, assert `[REDACTED:jwt]` appears

## Dev Notes

### Architecture patterns and constraints

- **JSON to stderr only** (ARCH-21): stdout is reserved for the MCP stdio transport (JSON-RPC). NEVER log to stdout.
- **Sanitiser at logger boundary** (ARCH-8): the filter is the single chokepoint. There is no app code that bypasses it. NEVER `System.out.println`, NEVER `printStackTrace`, NEVER `e.getMessage()` to a non-logger stream.
- **Pattern-based redaction** (ARCH-8): token-shaped strings (JWT, Bearer, opaque-base64), base64 blobs ≥ 32 chars, literal secret value, literal AuthToken value. The patterns are sensitive — over-redaction is acceptable (better to mask a non-secret that LOOKS like a token than miss a real one).
- **"Bearer" is pattern terminology only** (D35): twins transport uses the `AuthToken` header, NOT standard `Authorization: Bearer`. The Bearer-prefix redaction exists to catch any code that accidentally logs a standard Bearer token (defensive).
- **Sanitiser NEVER throws** (AC-8): a sanitiser exception would break logging. Catch all, fall back to original or `[REDACTED:error]`.
- **Recommended implementation pattern**: extend `Filter<ILoggingEvent>`, but instead of returning `DENY/ACCEPT` (which drops or passes whole events), use a `Converter` approach OR wrap the event to rewrite the formatted message. The cleanest Logback 1.5+ approach is a custom `<conversionRule>` that runs in the encoder pipeline. Verify at impl time via Context7 MCP.
- **JSON encoder choice**: prefer Logback 1.5's built-in JSON layout if available; else `logstash-logback-encoder` (G7 fallback, architecture §"Logging" line 150). Either is acceptable — pick what works with Boot 4.1's Logback version.

### Source tree components to touch

- `secrets/SecretsSanitiser.java` — the filter
- `secrets/SecretsSanitiserBootstrap.java` (optional) — `@EventListener<ApplicationStartedEvent>` to register the static secret value
- `config/LoggingConfig.java` — Spring-side logging hooks
- `src/main/resources/logback-spring.xml` — wiring
- Tests in `src/test/java/org/twins/mcp/secrets/`

### Testing standards summary

- JUnit 5 parameterized tests for the regex patterns (cover positive + negative cases per pattern).
- For the integration test (T5.7): use a Logback test appender (`ListAppender<ILoggingEvent>` from `logback-test`) to capture events in-process; verify the rendered JSON contains the redaction tag.

### Library/framework requirements

| Component | Version | Source |
|---|---|---|
| Logback | Boot 4.1 default (1.5.x) | transitive |
| `logstash-logback-encoder` | latest stable (7.4 or newer) | G7 fallback if built-in JSON layout unavailable |

Verify at impl time via Context7 MCP whether Boot 4.1's bundled Logback exposes a native JSON encoder.

### File structure requirements

- `SecretsSanitiser` is a POJO `Filter` implementation; the Spring context does not need to know about it (Logback instantiates it from XML).
- The dynamic registry MUST be a static singleton (Logback-managed instance, not Spring-managed). Story 1.4's `TokenHolder` registers via `SecretsSanitiser.getInstance().registerDynamic(...)`.

## Project Structure Notes

- Aligns with architecture §485-611 (`secrets/SecretsSanitiser.java`, `config/LoggingConfig.java`, `resources/logback-spring.xml`).
- Story 1.1's `application.yml` had no logging config; this story adds `logback-spring.xml` as the authoritative source (Boot auto-picks it up via convention).

## References

- Architecture:
  - §"Authentication & Security" line 237-238 (ARCH-8 secrets sanitiser)
  - §"Infrastructure & Deployment" line 278-280 (ARCH-21 logging)
  - §"Communication Patterns" line 387-400 (logging schema and levels)
  - §"Selected Starter" line 149-151 (Logback/Log4j2 choice; secrets masking reimplemented)
- Decision log: D35 ("Bearer" in sanitiser pattern names is terminology only)
- PRD: FR-TM-042 (output sanitization), NFR-TM-003 (secrets in logs)
- Epics: `_bmad-output/planning-artifacts/epics.md` §"Story 1.3"

## Dev Agent Record

### Agent Model Used

Claude (twins-mcp / GLM-5.2)

### Debug Log References

- `./gradlew clean test` — BUILD SUCCESSFUL, **57 tests, 0 failures, 0 skipped**:
  - Pre-existing Story 1.1 + 1.2 tests: 29 (TwinsCoreDtoSmokeTest, StartupEnvValidatorTest, PropertiesValidationTest).
  - Story 1.3 new: 18 unit tests in `SecretsSanitiserTest` (top-level + 5 AC-8 nested) + 5 integration tests in `SanitisingLayoutIntegrationTest`. Total new: 23.
- Manual smoke (`java -jar build/libs/twins-mcp-0.1.0-SNAPSHOT.jar`):
  - **Happy path** (`TWINS_M2M_CLIENT_SECRET=topsecret123`, valid env vars): STDOUT=0 bytes (AC-7 ✓), STDERR=6302 bytes all JSON (AC-1 ✓), `Started Application in ~13 s`.
  - **Secret-redaction check** (AC-3): grep for `topsecret123` in STDERR = 0 matches. The literal is registered via `LoggingConfig` at `ApplicationStartedEvent` and redacted on every log line.
  - **REDACTED breakdown in normal startup logs**: 3 occurrences of `[REDACTED:base64]` — all in Spring framework WARN messages about BeanPostProcessor (`serverAnnotatedMethodBeanPostProcessor`, 38 chars, surrounded by `[[...]]`). Acceptable per ARCH-8 ("over-redaction is acceptable"); these are one-off startup warnings, bean names in the same messages remain readable.
  - **0 occurrences of `[REDACTED:jwt]`** in normal startup — the boundary tightening (excluding `.` from lookarounds) prevents Java FQNs from matching the JWT regex.

### Completion Notes List

- **Implementation pattern deviation from T1.1.** Spec T1.1 says "extending `Filter<ILoggingEvent>`"; Dev Notes recommended a Converter or event-wrapping approach because Logback `Filter.decide()` can only ACCEPT/DENY/NEUTRAL — it cannot rewrite the message. We went with a third option: `SanitisingLayout extends LayoutBase<ILoggingEvent>`, wrapping a delegate layout (LogstashLayout) and post-processing the rendered string through `SecretsSanitiser.sanitise(...)`. This is functionally equivalent to the "wrap the event" recommendation, simpler to wire, and works with any underlying encoder. T1.1's intent (sanitiser is a Logback-managed POJO) is preserved — the layout is instantiated by Logback from XML.
- **Filter-vs-Layout trade-off.** Post-render sanitisation processes the entire JSON line (keys + values), which over-redacts long Java class names that look base64-shaped. To minimise this, both JWT and BASE64 regexes exclude `.` from lookbehind/lookahead boundaries — so a class name at the end of an FQN (preceded by `.`) is not matched. Residual over-redaction is acceptable per ARCH-8.
- **`logstash-logback-encoder:9.0` chosen.** WebSearch confirmed 9.0 is the latest GA (Oct 2025). Logback 1.5.x bundled with Boot 4.1 has no native JSON encoder, so the logstash dependency is required. The spec's fallback (Boot built-in JSON encoder) does not exist.
- **JWT length check applied outside regex.** The spec's regex (`^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$`) matches any 3-segment dotted string. AC-2 also requires ≥ 32 chars total. Implemented as a custom `replaceJwt` loop that runs the regex, checks `matcher.group().length() >= 32`, and only then replaces. This avoids false positives like `v0.1.0` (6 chars).
- **BASE64 regex narrowed.** Spec AC-5 uses `[A-Za-z0-9+/=]{32,}`. Implementation uses `[A-Za-z0-9+/]{32,}={0,2}` — padding `=` is allowed only at the end (0-2 chars), not as a body char. This prevents `key=value` contexts like `v=abc...` (where `=` precedes the blob) from over-matching.
- **Unicode case-insensitive secret matching.** Java's `Pattern.CASE_INSENSITIVE` alone is ASCII-only. Combined with `Pattern.UNICODE_CASE` so Cyrillic (and other non-ASCII) secrets match case-insensitively per AC-3 (test `unicodeSecretLiteral` covers this).
- **Defensive `SanitisingLayout.start()`.** Logback's lifecycle requires nested layouts to be started explicitly. `SanitisingLayout.start()` starts the delegate if it isn't already, sets the context, and only marks itself started if the delegate is present.
- **T3.2 (MDC `tool` key) deferred.** Not required by any AC; will be added in Story 1.5 when tools are registered.
- **Smoke over-redaction note.** 3 `[REDACTED:base64]` artifacts remain in normal startup logs, all from Spring's BeanPostProcessor WARN. The class names are inside `[[...]]` brackets, which are not in the boundary exclusion set, so 32+ char names still match. Tightening further would require unreliable heuristics (mixed-case detection, digit presence) — not worth the false-positive risk.

### File List

- `gradle/libs.versions.toml` (modified — added `logstash-logback-encoder` version + library)
- `build.gradle` (modified — added `logstash-logback-encoder` dep)
- `src/main/resources/logback-spring.xml` (modified — full JSON encoder + SanitisingLayout wiring)
- `src/main/java/org/twins/mcp/secrets/SecretsSanitiser.java` (new — redaction engine)
- `src/main/java/org/twins/mcp/secrets/SanitisingLayout.java` (new — Logback Layout)
- `src/main/java/org/twins/mcp/config/LoggingConfig.java` (new — registers static secret literal)
- `src/test/java/org/twins/mcp/secrets/SecretsSanitiserTest.java` (new — 18 unit tests, AC-2..AC-5, AC-8)
- `src/test/java/org/twins/mcp/secrets/SanitisingLayoutIntegrationTest.java` (new — 5 integration tests through real Logback pipeline)

## Change Log

| Date | Change |
|---|---|
| 2026-07-01 | Story created from Epic 1 breakdown (bmad-create-story) |
| 2026-07-09 | Implementation: SecretsSanitiser + SanitisingLayout + LoggingConfig + logstash-logback-encoder 9.0 + 23 new tests (Story 1.3 → review) |
