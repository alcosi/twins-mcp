# twins-mcp

A [Model Context Protocol](https://modelcontextprotocol.io) server that exposes
[twins](https://github.com/alcosi/twins) entity-model data to LLM agents. Backend-only;
speaks stdio; built on Spring Boot 4.1 + Spring AI 2.0.

> **Status:** skeleton (Story 1.1). Tools, distribution, and full docs land in
> later stories. See `_bmad-output/planning-artifacts/epics.md` for the roadmap.

## Requirements

- **Java 25 LTS** (no `--enable-preview`).
- **Gradle 9.6.1** (via wrapper — no separate install needed).
- **Git** with submodule support.

## Clone

```bash
git clone --recurse-submodules https://github.com/alcosi/twins-mcp.git
cd twins-mcp
```

If you already cloned without `--recurse-submodules`:

```bash
git submodule update --init --recursive
```

The `vendor/twins/` submodule pins the twins source tree used by the
no-deprecated-endpoints CI gate (ARCH-20). It is not required at runtime.

## Build

```bash
./gradlew build
```

Produces `build/libs/twins-mcp-<version>.jar` (fat JAR).

## Run (smoke — full MCP client wiring arrives in later stories)

```bash
export TWINS_BASE_URL=http://localhost:8080
export TWINS_DOMAIN_ID=00000000-0000-0000-0000-000000000000
export TWINS_M2M_CLIENT_ID=...
export TWINS_M2M_CLIENT_SECRET=...
java -jar build/libs/twins-mcp-<version>.jar
```

All logs go to stderr; stdout is reserved for the MCP JSON-RPC transport.

## License

Apache 2.0 — see [LICENSE](LICENSE).
