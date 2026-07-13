---
baseline_commit: d9000c46686efaa32537739dd90b2f1e7fa5e742
---

# Story 1.1: Project Initialization & Build Skeleton

Status: done

## Story

As a **developer**,
I want **a clean Gradle project that builds and boots a Spring Boot application with all required dependencies**,
so that **subsequent stories have a stable foundation to add MCP tooling on top of**.

## Acceptance Criteria

1. **AC-1 (build)** — Fresh clone with Java 25 → `./gradlew build` succeeds with zero compile errors and produces `build/libs/twins-mcp-<version>.jar` via `bootJar`. The `vendor/twins/` git submodule initialises (per ARCH-20 staging — full CI gate arrives in Epic 4).
2. **AC-2 (boot smoke)** — `./gradlew bootRun` with valid env vars (`TWINS_BASE_URL`, `TWINS_DOMAIN_ID`, `TWINS_M2M_CLIENT_ID`, `TWINS_M2M_CLIENT_SECRET`) logs `Started Application` to **stderr**. Stdout receives zero log lines (preserves the stdio transport channel for FR-TM-022).
3. **AC-3 (deps & plugins)** — `build.gradle` declares: Boot 4.1.0 plugin, Java 25 toolchain (no `--enable-preview` per D34), `org.springframework.ai:spring-ai-starter-mcp-server` (modern artifact name — legacy `spring-ai-mcp-server-spring-boot-starter` is abandoned at 1.0.0-M6), `com.alcosi.twins:twins-core-dto:1.4.191`. Spring AI 2.0.0 BOM imported via Gradle `platform()` (the `io.spring.dependency-management` plugin originally named here was dropped during implementation — v1.1.8 is not published for Boot 4.x; the Boot 4-recommended native `platform()` is the documented substitute; see Deviation 1 / Decision-3 / architecture §109). `org.gradle.toolchains.foojay-resolver-convention` plugin added to auto-provision the Java 25 toolchain on machines without a local JDK 25 (added 2026-07-08 code review). Version catalog exists at `gradle/libs.versions.toml` (per Pattern Enforcement §7 — no inline version strings).
4. **AC-4 (package layout)** — Skeleton packages exist under `src/main/java/org/twins/mcp/` (empty packages are OK — they will be populated by later stories): `app/`, `config/`, `client/`, `client/endpoint/`, `tool/`, `tool/catalog/`, `tool/glossary/`, `response/`, `error/`, `secrets/`, `metrics/`. `app/Application.java` contains `@SpringBootApplication` and `main()`.
5. **AC-5 (DTO compile proof)** — Build succeeds with `twins-core-dto:1.4.191` on the classpath, resolving OQ-ARCH-1 (Java 25 compile of twins-core-dto). A trivial smoke test in `src/test/java/` instantiates one DTO (e.g., `new TwinClassDTO()` or similar from the JAR) to prove the dep is wired — name it `TwinsCoreDtoSmokeTest`.
6. **AC-6 (vendoring staged)** — `vendor/twins/` exists as a registered git submodule pointing at the twins repo (default branch is fine; pin to release tag happens in Epic 4 / ARCH-SETUP-20). `.gitmodules` committed. No code in twins-mcp reads from `vendor/twins/` yet — that lives in Epic 4.
7. **AC-7 (resources)** — `src/main/resources/application.yml` exists with a minimal `spring.application.name: twins-mcp` and `spring.main.web-application-type: none` (we are a stdio process; no servlet container — Undertow/Tomcat/Jetty must NOT start). `logback-spring.xml` placeholder can defer to Story 1.3.
8. **AC-8 (no stdout pollution)** — Verifiable by running `./gradlew bootRun 2>/dev/null` (i.e., suppress stderr) — stdout must remain empty during normal startup. Spring Boot's default banner goes to stdout by default; disable it via `spring.main.banner-mode = off` (or `System.setOut` redirection is NOT acceptable — use the Spring property).

## Tasks / Subtasks

- [x] **T1 — Gradle skeleton** (AC-1, AC-3)
  - [x] T1.1 Create `settings.gradle` with `rootProject.name = 'twins-mcp'`
  - [x] T1.2 Create `gradle.properties` (group, version, optional `docker`/`profile` placeholders)
  - [x] T1.3 Create `gradle/libs.versions.toml` with pins: spring-boot `4.1.0`, spring-ai `2.0.0`, twins-core-dto `1.4.191`, java `25`
  - [x] T1.4 Create `build.gradle` with plugins (`java`, `org.springframework.boot`), Java 25 toolchain, Spring AI BOM via `platform()`, dependencies per AC-3, `bootJar` enabled
  - [x] T1.5 Add Gradle wrapper (Gradle 9.6.1 — pinned via `gradle/wrapper/gradle-wrapper.properties`)
- [x] **T2 — Package skeleton** (AC-4)
  - [x] T2.1 Document the package layout in `package-info.java` (full layout delivered by later stories per architecture §485-611; `app/` is the only concrete package in this story)
  - [x] T2.2 Create `app/Application.java` with `@SpringBootApplication` and `public static void main(String[] args)`
  - [x] T2.3 Create `src/test/java/org/twins/mcp/` mirror
- [x] **T3 — DTO smoke test** (AC-5)
  - [x] T3.1 Identified `org.twins.core.dto.rest.twinclass.TwinClassDTOv1` in `twins-core-dto:1.4.191` (verified via `jar tf` on the gradle-cached artifact)
  - [x] T3.2 `TwinsCoreDtoSmokeTest` instantiates the DTO and asserts default-field sanity — passes under Java 25.0.2 (resolves OQ-ARCH-1)
- [x] **T4 — Resources & banner** (AC-2, AC-7, AC-8)
  - [x] T4.1 `src/main/resources/application.yml` with `spring.application.name`, `spring.main.web-application-type: none`, `spring.main.banner-mode: off`
  - [x] T4.2 `src/main/resources/logback-spring.xml` placeholder routing all logs to stderr (full JSON encoder + sanitiser in Story 1.3). Verified via `java -jar`: STDERR=3663 bytes, STDOUT=0 bytes.
- [x] **T5 — Vendor submodule** (AC-6)
  - [x] T5.1 `git submodule add https://github.com/alcosi/twins.git vendor/twins`
  - [x] T5.2 Committed `.gitmodules` (submodule pins HEAD `b0e7677a5`)
  - [x] T5.3 README stub with `git clone --recurse-submodules` invocation
- [x] **T6 — Repo hygiene** (cross-cutting)
  - [x] T6.1 `.gitignore` (Gradle/IDE/Java/OS)
  - [x] T6.2 `.gitattributes` (`* text=auto eol=lf`, `*.bat eol=crlf`)
  - [x] T6.3 `.editorconfig` (UTF-8, LF, 4-space Java, 2-space YAML/JSON/MD/TOML)
  - [x] T6.4 `LICENSE` (Apache 2.0 full text from apache.org)
- [x] **T7 — Verify** (all ACs)
  - [x] T7.1 `./gradlew clean build` — green; produces `build/libs/twins-mcp-0.1.0-SNAPSHOT.jar` (60 MB fat JAR)
  - [x] T7.2 `java -jar build/libs/twins-mcp-0.1.0-SNAPSHOT.jar` with env vars → `Started Application` on stderr, stdout empty
  - [x] T7.3 `TwinsCoreDtoSmokeTest` passes

## Dev Notes

### Architecture patterns and constraints

- **Java 25, no `--enable-preview`** (D34). The original NFR-TM-006 wording (Java 21 + preview) is stale; the architecture decision supersedes it. Do NOT enable preview in `build.gradle`'s `java { }` block or via `jvmArgs`.
- **Standalone Gradle build/repo** (D32). This OVERRIDES D9/D18/FR-TM-060 which previously said "module inside twins project". Do NOT create a nested Gradle project; this is the root project.
- **DTO-only reuse from twins** (D33, FR-TM-062). `com.alcosi.twins:twins-core-dto:1.4.191` is the only twins artifact on the classpath. Do NOT add dependencies on the full twins Spring Boot app. Auth client, REST client, OpenAPI config, `ApiUser` thread-local, `findEntitySafe()`/`isEntityReadDenied()` are NOT reused — they are reimplemented in later stories (1.4 onward).
- **`spring-ai-starter-mcp-server`** — modern artifact name. The legacy `spring-ai-mcp-server-spring-boot-starter` was abandoned at M6; do NOT use it.
- **No servlet container.** `spring.main.web-application-type: none` is mandatory. We are a stdio process; Boot must NOT start Undertow/Tomcat/Jetty. The MCP server uses stdio transport, not HTTP.
- **Pattern Enforcement** (architecture §422-436): all version pins live in `gradle/libs.versions.toml`; no inline version strings in `build.gradle`. Spotless + Checkstyle will be wired in Epic 4 (ARCH-SETUP-18), but the version discipline applies from day one.

### Source tree components to touch

This story creates new files only — no existing files modified. The full target structure is documented in `_bmad-output/planning-artifacts/architecture.md` §485-611 (Complete Project Directory Structure). This story delivers only the top-of-tree skeleton:

```
twins-mcp/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/...
├── gradlew / gradlew.bat
├── LICENSE
├── .gitignore / .gitattributes / .editorconfig
├── vendor/
│   └── twins/                       # git submodule (AC-6)
└── src/
    ├── main/
    │   ├── java/org/twins/mcp/
    │   │   └── app/Application.java
    │   └── resources/application.yml
    └── test/
        └── java/org/twins/mcp/
            └── TwinsCoreDtoSmokeTest.java
```

Later stories populate the remaining packages (`config/`, `client/`, `tool/`, etc.). They are NOT created as empty directories in this story — Git doesn't track empty dirs and the `Application.java` file is sufficient for the boot smoke. Other packages are added by later stories when they have content. (AC-4 lists the packages — interpret it as "the layout is agreed in architecture and will be delivered by later stories"; only `app/` is concrete here.)

### Testing standards summary

- **JUnit 5** + Spring Boot Test (Boot 4 ships both transitively). No extra test deps needed in this story.
- The smoke test (`TwinsCoreDtoSmokeTest`) is a plain JUnit test (no Spring context) — just `assertTrue(dto != null)` or equivalent.
- Integration tests (`*IT.java`, Testcontainers, ARCH-SETUP-14) come in Story 1.8 — NOT in this story.
- Test source mirror: `src/test/java/org/twins/mcp/`.

### Library/framework requirements (pinned versions)

| Component | Version | Source |
|---|---|---|
| Spring Boot | 4.1.0 | D34, architecture §153-163 |
| Spring AI | 2.0.0 (BOM) | D34 |
| spring-ai-starter-mcp-server | via BOM | D34, FR-TM-061 |
| twins-core-dto | 1.4.191 | D33, FR-TM-062 |
| Java toolchain | 25 (LTS) | D34, NFR-TM-006 (modified) |
| Gradle | 9.6+ | implied by Boot 4.1 |
| MCP spec | 2025-06-18 | NFR-TM-006 (informational; not a code dep) |

**OQ-ARCH-1 risk:** Verify `twins-core-dto:1.4.191` compiles cleanly under Java 25 (no removed-API usage). T3 smoke test is the verification.

### File structure requirements

- `build.gradle` (Groovy DSL is fine; Kotlin DSL also acceptable — pick one and stay consistent. The architecture doc's example uses Groovy).
- `gradle/libs.versions.toml` is the single source of truth for versions.
- `Application.java` package: `org.twins.mcp.app`.
- `application.yml` uses kebab-case keys (`spring.main.web-application-type`, etc.).

## Project Structure Notes

- **Alignment with `architecture.md` §485-611:** Target structure is fully defined there; this story delivers only the root-of-tree skeleton. No conflicts or variances.
- **`web-application-type: none`:** Critical for stdio MCP server. The spring-ai-starter-mcp-server does NOT require a servlet container; starting one would compete with the JSON-RPC stdio channel.
- **Vendor submodule placement:** `vendor/twins/` per ARCH-20. Default branch is acceptable for Story 1.1; pin to release tag in Epic 4 (ARCH-SETUP-20).
- **No `module-info.java`:** JPMS deferred per architecture §Service Boundaries ("Boot 4 itself is still transitioning"). Do NOT add `module-info.java` in v1.

## References

- Architecture: `_bmad-output/planning-artifacts/architecture.md`
  - §80-191 — Starter Template Evaluation (Option B selected, rationale)
  - §143-163 — Architectural decisions provided by starter + version pinning
  - §165-182 — PRD/decision-log deltas (D32-D34)
  - §485-611 — Complete project directory structure
  - §322-436 — Implementation patterns (naming, structure, format, process, enforcement)
- Decision log: `_bmad-output/planning-artifacts/prds/prd-twins-mcp-2026-06-23/.decision-log.md`
  - D32 — standalone build on Boot 4
  - D33 — DTO reuse via Maven Central artifact
  - D34 — version pins for v1 (Java 25, no preview)
- PRD: `_bmad-output/planning-artifacts/prds/prd-twins-mcp-2026-06-23/prd.md`
  - FR-TM-060 — module layout (overridden by D32)
  - FR-TM-061 — Spring AI MCP server starter usage
  - FR-TM-062 — DTO reuse (partially preserved by D33)
  - NFR-TM-006 — compatibility (modified by D34: Java 25 LTS, no `--enable-preview`)
- Epics: `_bmad-output/planning-artifacts/epics.md` §"Story 1.1: Project Initialization & Build Skeleton"
- twins source (submodule target): `D:/work/esas/sources/java/twins` (sibling local clone; OSS GitHub: alcosi/twins)
- Memory: `com.alcosi.twins:twins-core-dto` on Maven Central, DTO-only JAR — verified by `[[twins-core-dto-maven-artifact]]`

## Dev Agent Record

### Agent Model Used

Claude (twins-mcp / GLM-5.2)

### Debug Log References

- `java -jar` stderr capture (smoke run with valid env vars): STDERR=3663 bytes, STDOUT=0 bytes — AC-2/AC-8 satisfied.
- `./gradlew clean build`: BUILD SUCCESSFUL in 13s. Fat JAR `build/libs/twins-mcp-0.1.0-SNAPSHOT.jar` (60 MB).
- `./gradlew test`: 1 test, 0 failures (`TwinsCoreDtoSmokeTest`).
- Build also produces `twins-mcp-0.1.0-SNAPSHOT-plain.jar` (2 KB) — the plain JAR, not the executable.

### Completion Notes List

- **AC-1 / AC-3:** Boot 4.1.0 plugin, Spring AI 2.0.0 BOM (via Gradle `platform()` — see Deviation 1), Java 25 toolchain (no `--enable-preview`), `spring-ai-starter-mcp-server`, `twins-core-dto:1.4.191`. Version catalog at `gradle/libs.versions.toml`.
- **AC-5 / OQ-ARCH-1 resolved:** `twins-core-dto:1.4.191` compiles and loads under Java 25.0.2 LTS. Smoke test instantiates `org.twins.core.dto.rest.twinclass.TwinClassDTOv1`.
- **AC-6:** `vendor/twins` submodule pins `https://github.com/alcosi/twins.git` at HEAD `b0e7677a5`. No code reads from it yet.
- **AC-7 / AC-8:** `application.yml` + minimal `logback-spring.xml` (placeholder; full JSON+sanitiser in Story 1.3). Verified stdout=0 on `java -jar` run.
- **Gradle version bumped to 9.6.1:** architecture specified "9.6+" — verified Java 25 support requires Gradle 9.1+ (Gradle 8.x fails with "Unsupported class file major version 69").

### Deviations from story spec

1. **`io.spring.dependency-management` plugin dropped.** Architecture §107-110 specified plugin version `1.1.8`, but that version is not published (404 on Maven Central / plugin portal). The Boot 4-recommended approach is Gradle's native BOM support via `implementation platform(libs.spring.ai.bom)`. This is functionally equivalent for our needs (managing spring-ai-* versions) and avoids the stale plugin pin. The Spring Boot Gradle plugin continues to manage its own starter versions.
2. **Gradle version discovered empirically.** Architecture said "Gradle 9.6+" — first tried 8.13, which fails on Java 25 bytecode. Bumped wrapper to 9.6.1 (latest stable, 2026-06-27 release) which has full Java 25 support.
3. **No empty package directories.** Per architecture §485-611 the target has 11 packages, but Git doesn't track empty dirs. Story AC-4 lists packages — interpreted as "layout agreed, delivered by later stories when they have content". `app/` is the only concrete package here. The full layout is documented in `package-info.java`.

### File List

- `settings.gradle` (new)
- `gradle.properties` (new)
- `gradle/libs.versions.toml` (new)
- `build.gradle` (new)
- `gradle/wrapper/gradle-wrapper.jar` (new, Gradle 9.6.1)
- `gradle/wrapper/gradle-wrapper.properties` (new)
- `gradlew` (new)
- `gradlew.bat` (new)
- `src/main/java/org/twins/mcp/package-info.java` (new)
- `src/main/java/org/twins/mcp/app/Application.java` (new)
- `src/main/resources/application.yml` (new)
- `src/main/resources/logback-spring.xml` (new, placeholder)
- `src/test/java/org/twins/mcp/TwinsCoreDtoSmokeTest.java` (new)
- `README.md` (new, stub)
- `LICENSE` (new, Apache 2.0)
- `.gitignore` (new)
- `.gitattributes` (new)
- `.editorconfig` (new)
- `.gitmodules` (new)
- `vendor/twins` (new submodule, pins `https://github.com/alcosi/twins.git` @ `b0e7677a5`)

## Review Findings

Code review run on 2026-07-08 via `bmad-code-review` (Blind Hunter + Edge Case Hunter + Acceptance Auditor). Diff scope: staged changes for Story 1.1 implementation files (20 files, ~571 lines).

### Decision-needed — RESOLVED 2026-07-08

- [x] [Review][Decision] **`spring-boot-starter-web` on classpath contradicts strict reading of AC-7** [`build.gradle:24` + `application.yml`] — **Decision: drop `starter-web`, use `spring-boot-starter` + `spring-web` directly.** RestClient works without MVC/Tomcat. → converted to [Patch] below.
- [x] [Review][Decision] **AC-4 partial — only `app/` materialised on disk** [`src/main/java/org/twins/mcp/`] — **Decision: leave as-is.** Lazy package creation in later stories is acceptable per Deviation 3. → dismissed.
- [x] [Review][Decision] **Deviation 1: `io.spring.dependency-management` plugin dropped** [`build.gradle:1-4`] — **Decision: accept deviation + update docs.** Spec/AC-3 and architecture §107-110 will be updated to reflect Gradle `platform()` approach. OQ-ARCH-3 closed as "verified via substitution". → converted to [Patch] below.

### Patch

- [x] [Review][Patch] **`version` declared in two places** [`build.gradle:7` + `gradle.properties:2`] — `build.gradle` `version = '0.1.0-SNAPSHOT'` silently shadows `gradle.properties` `version=0.1.0-SNAPSHOT`. Single source of truth needed. **Applied 2026-07-08:** removed `version = '...'` from `build.gradle`; `gradle.properties` is now the single source.
- [x] [Review][Patch] **`gradle-wrapper.properties` missing `distributionSha256Sum`** [`gradle/wrapper/gradle-wrapper.properties:3`] — supply-chain integrity gap; Gradle explicitly recommends pinning the SHA-256 of the distribution zip. **Applied 2026-07-08:** added `distributionSha256Sum=9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14` (official Gradle SHA).
- [x] [Review][Patch] **`build.gradle` missing `foojay-resolver-convention` plugin** [`build.gradle:1-4`] — without toolchain auto-provisioning, fresh clone on a machine without JDK 25 fails opaquely with "No matching toolchain". **Applied 2026-07-08:** added to `settings.gradle` (it is a settings plugin, not a project plugin) — `id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'`.
- [x] [Review][Patch] **Logback encoder missing `<charset>`** [`logback-spring.xml:18-20`] — platform-default charset on Windows runtime JVMs (Cp1252); non-ASCII content (Russian log lines, trademark symbols) mojibakes. **Applied 2026-07-08:** added `<charset>UTF-8</charset>` to the STDERR encoder.
- [x] [Review][Patch] **`LICENSE` has stray blank line at top** [`LICENSE:1`] — canonical Apache 2.0 LICENSE starts with "Apache License" on line 1; license-detection tooling (FOSSA, ScanCode, GitHub license-checker) may misclassify as "unknown". **Applied 2026-07-08:** removed the leading blank line.
- [x] [Review][Patch] **`.gitignore` rule `*.local` too broad** [`.gitignore:33`] — suffix match swallows legitimate `.local` files (e.g., locale bundle fragments, third-party configs). `.env.local` already covered on line 32; `*.local` reaches further than intended. **Applied 2026-07-08:** removed `*.local`; kept `.env` and `.env.local`; added `!.env.example` whitelist escape.
- [x] [Review][Patch] **`TwinsCoreDtoSmokeTest` name oversells what's tested** [`TwinsCoreDtoSmokeTest.java`] — `assertThat(dto).isNotNull()` after `new` is tautological; `getId() == null` only verifies field initializer. The actual purpose (per spec) is class-load proof for OQ-ARCH-1. Rename to `twinClassDtoLoadsUnderJava25` + clarifying comment, or add a meaningful assertion. **Applied 2026-07-08:** renamed test method to `twinClassDtoClassLoadsUnderJava25`; rewrote Javadoc to be explicit about the class-load mechanism; removed the misleading `getId()` assertion.
- [x] [Review][Patch] **`.gitattributes` doesn't anchor `gradlew` to LF** [`.gitattributes`] — extensionless `gradlew` checked out on Windows with `core.autocrlf=true` gets CRLF, breaking the `#!/bin/sh` shebang under WSL/Linux containers. Add explicit `/gradlew text eol=lf`. **Applied 2026-07-08:** added `/gradlew text eol=lf` line.
- [x] [Review][Patch] **Drop `spring-boot-starter-web`, use `spring-boot-starter` + `spring-web` directly** [`build.gradle:24`] — per Decision-1. RestClient works without MVC/Tomcat stack. Add `spring-web` to version catalog. **Applied 2026-07-08:** removed `spring-boot-starter-web` from `libs.versions.toml` and `build.gradle`; added `spring-web = { module = "org.springframework:spring-web" }` (version managed by Spring Boot BOM imported via `platform(libs.spring.boot.bom)`). Required also adding the Spring Boot BOM as a `platform()` import — Spring AI BOM alone did not export spring-web's version.
- [x] [Review][Patch] **Update spec/AC-3 + architecture §107-110 to reflect Gradle `platform()` approach** [`_bmad-output/implementation-artifacts/1-1-...md` AC-3, `_bmad-output/planning-artifacts/architecture.md` §107-110] — per Decision-3. Close OQ-ARCH-3 as "verified via substitution". **Applied 2026-07-08:** updated AC-3 wording; rewrote architecture §107-110 code block (removed `io.spring.dependency-management`, added foojay plugin, replaced starter-web with spring-web); struck through OQ-ARCH-3 with resolution note.

### Defer

- [x] [Review][Defer] **Logback `immediateFlush=true` synchronous flush under load** [`logback-spring.xml:17`] — deferred to Story 1.3 (full JSON encoder + SecretsSanitiser + AsyncAppender will replace this placeholder).
- [x] [Review][Defer] **Submodule pinned to bare SHA, no branch/tag** [`.gitmodules` + `vendor/twins`] — deferred to Epic 4 (ARCH-SETUP-20 explicitly stages release-tag pinning).
- [x] [Review][Defer] **No `SPRING_PROFILES_ACTIVE` fail-fast** [`application.yml`] — out of scope; will surface when runtime profiles matter.
- [x] [Review][Defer] **No stdout guard / no regression test** [`application.yml` + `logback-spring.xml`] — Story 1.3 installs SecretsSanitiser; stdout-empty regression test belongs there.
- [x] [Review][Defer] **"no `--enable-preview`" not enforced by build** [`README.md:120`] — minor; could add compiler args to fail on preview, but no current usage.

### Dismissed

8 findings dismissed as noise / false positives — primarily Blind Hunter items stemming from stale training data (claims that Java 25 / Boot 4.1 / Spring AI 2.0 don't exist, that the standard Gradle wrapper script is "legacy", that the bootJar isn't wired). Plus duplicates, intentional design choices (package-info documents future packages), and non-actionable smells (caching/parallel in single-module build, agreement between gitattributes and editorconfig).

## Change Log

| Date | Change |
|---|---|
| 2026-07-01 | Story created from Epic 1 breakdown (bmad-create-story) |
| 2026-07-06 | Implementation: project init, build, smoke test, submodule, hygiene files (Story 1.1 → review) |
| 2026-07-08 | Code review via bmad-code-review: 3 decision-needed, 8 patch, 5 defer, 8 dismissed
