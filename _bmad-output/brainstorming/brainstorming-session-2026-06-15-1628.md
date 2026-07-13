---
stepsCompleted: [1, 2]
inputDocuments:
  - 'D:/work/esas/sources/java/twins.branch2/docs/glossary.md'
  - 'D:/work/esas/sources/java/twins.branch2/_bmad-output/project-context.md'
session_topic: 'MCP server for twins framework — expose glossary + domain TwinClass descriptions'
session_goals: 'Design MCP server: tool surface, data sources (glossary.md / live REST), first iteration scope'
selected_approach: 'ai-recommended'
techniques_used: ['First Principles Thinking', 'Morphological Analysis', 'Reverse Brainstorming']
ideas_generated: []
context_file: ''
---

# Brainstorming Session Results

**Facilitator:** Nikita
**Date:** 2026-06-15 (session resumed 2026-06-17)

## Session Overview

**Topic:** MCP server `twins-mcp` exposing twins concepts to LLM agents.

**Host project:** `twins` — multi-tenant entity management platform.
- Source: `D:\work\esas\sources\java\twins` (and `.branch2` working copy), GitHub: https://github.com/alcosi/twins
- Java 21 + Spring Boot 3.5.3 + Undertow + PostgreSQL + Hibernate + Flyway
- REST via Spring MVC + springdoc OpenAPI
- Core domain model: Domain → TwinClass → Twin, with Twinflow (state machine), TwinFactory (pipelines), Link/TwinLink, permissions (5 grant types), I18n, Featurer (Cambium) pluggable system

**Goals (initial):**
1. MCP server exposes twins **vocabulary / glossary** (canonical `docs/glossary.md` exists).
2. MCP server exposes **description of classes of a concrete domain** (TwinClass + fields + twinflow + factories).
3. twins accessible via REST → MCP talks to live instance, or reads static docs, or both.

### Context Guidance

- **Canonical glossary already exists**: `docs/glossary.md` (~430 lines, 9 sections: Core, Workflow, Multi-Tenancy, Permissions, Content, Cross-Cutting, Field Value Tables, Field Rules, Other). Each entry has: definition, JPA class path, table name, key fields, relationships, notes.
- **Lean project context exists**: `_bmad-output/project-context.md` (120 rules across stack, language, framework, testing, quality, workflow).
- **REST API docs**: `docs/rest_api.md`, `docs/api_starter.md`, `docs/api_*_architecture.md`.
- **DTO conventions** are strict (`@Schema`, `RqDTOv1`/`RsDTOv1`/`*DTOv1` suffixes, `@RelatedObject` for resolved UUIDs).
- **Multi-tenant**: every entity carries `domainId`; reads must go through `findEntitySafe()` with `isEntityReadDenied()` gate.
- **Auth**: `ApiUser` thread-local; permissions via `@ProtectedBy`; routes under `controller/rest/{priv,pub,auth}`.

### Session Setup

_Fresh session. Setup confirmed with glossary + project-context loaded._
_Waiting on user's approach selection._

### Scope (locked 2026-06-17)

| Dimension | Decision |
|---|---|
| Source | Dynamic REST — MCP calls twins REST APIs for domain description |
| Glossary | Deferred — user will revisit later |
| First-iteration scope | Domain description: TwinClasses, their fields, inter-class relations |
| MCP transport | **Both** — stdio (local dev) and HTTP/SSE (cloud), selectable via `TWINS_MCP_TRANSPORT` env var |
| twins connection | `TWINS_BASE_URL` env var — local or remote |
| Auth | stdio: env var `TWINS_API_TOKEN`. HTTP/SSE: Bearer token + HTTPS |
| Access mode | Read-only |
| Consumer | Claude Code (local) + any MCP client (cloud) |
| Service account | Dedicated view-only user per domain |

Open question — which twins REST endpoints expose:
- All TwinClasses in a domain
- Fields of a TwinClass
- Relations (links) between TwinClasses

These will be discovered during ideation (or by reading `docs/rest_api.md`).
