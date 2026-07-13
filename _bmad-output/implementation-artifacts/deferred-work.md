# Deferred Work

Track items surfaced during code review that are real but out of scope for the current story. Each entry: where it came from, what it is, why deferred.

## Deferred from: code review of 1-2-connection-config-env-validator (2026-07-08)

- **Integration test for non-zero exit code** [`StartupEnvValidatorTest`] — spec T4.2 only requires manual smoke (verified `EXIT=1` for missing-var and malformed-URL cases). Automated integration test via `SpringApplication.exit` would harden regression coverage but is not spec-required.
- **`PROBE_TIMEOUT_MILLIS` not operator-configurable** [`StartupEnvValidator.java:42`] — hardcoded 2000 ms per AC-6 upper bound. High-latency environments may want longer. Defer to a future "operational tuning" story.
- **URL with userinfo (`http://user:pass@host`)** [`StartupEnvValidator.java`] — URI parses fine, but embedded credentials bypass the M2M auth model. Defer to Story 1.4 (HTTP client wiring) — reject userinfo at the RestClient boundary.
- **Env-var name mapping duplicated between validator switch and YAML `${TWINS_*:}` placeholders** — future rename would desync. Architectural; defer to a config-refactor story if env var names churn.
- **`clientSecret` CRLF validation** [`M2mCredentialsProperties.java:24`] — secret with `\r\n` could enable header injection IF it ever reaches a header. Story 1.4 wires M2M client; secret goes in JSON body, not headers — but defense-in-depth `@Pattern` rejecting `\r|\n` could be added there.

## Deferred from: code review of 1-1-project-init-build-skeleton (2026-07-08)

- **Logback `immediateFlush=true` synchronous flush under load** [`logback-spring.xml:17`] — every log line synchronously flushes to stderr; under load this can stall the MCP transport thread. Deferred because Story 1.3 will replace this placeholder with full JSON encoder + SecretsSanitiser + AsyncAppender.
- **Submodule pinned to bare SHA, no branch/tag** [`.gitmodules` + `vendor/twins`] — pinned to commit `b0e7677a5` only; force-push or GC on `alcosi/twins` would break the clone. Deferred to Epic 4 (ARCH-SETUP-20 explicitly stages release-tag pinning).
- **No `SPRING_PROFILES_ACTIVE` fail-fast** [`application.yml`] — unknown profile silently falls back to base config. Out of scope for Story 1.1; surfaces when runtime profiles matter (likely Story 1.2 or later config story).
- **No stdout guard / no regression test** [`application.yml` + `logback-spring.xml`] — Logback routes to stderr, but a misbehaving library can still write to stdout; no test catches the regression. Story 1.3 installs SecretsSanitiser; stdout-empty regression test belongs there.
- **"no `--enable-preview`" claim not enforced by build** [`README.md:120`] — README asserts no preview, but `build.gradle` does not actively reject preview usage. Minor; could add compiler args, but no current usage to guard against.
