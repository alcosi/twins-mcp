# Reconcile: Brainstorming Artifact vs PRD Draft

**Source:** `_bmad-output/brainstorming/brainstorming-session-2026-06-15-1628.md`
**PRD:** `_bmad-output/planning-artifacts/prds/prd-twins-mcp-2026-06-23/prd.md`
**Decision log:** `.decision-log.md` (D1–D30)
**Date:** 2026-06-24

## Method

Extracted three material blocks from the brainstorming:
1. Scope table (lines 50–60) — 9 dimensions.
2. Context Guidance (lines 35–41) — qualitative twins-context anchors.
3. Open questions (lines 62–67) — endpoint-discovery items.

For each, checked: present in PRD? Superseded by a recorded decision (D1–D30)? Falls through cracks?

The decision log already captures D7 (transport), D8 (auth), D19 (stdio-only), D30 (glossary-as-Twins). Those divergences are NOT re-derived here.

## Verdict

**Minor gaps.** The PRD is substantively faithful on every scope-table dimension and resolves all three brainstorming open questions. The gaps are qualitative Context-Guidance items the FR structure dropped without recording a deliberate decision — they matter for downstream architecture, not for v1 scope.

## Gaps (Brainstorming → PRD → Fix)

### G1. Multi-tenant read-safety primitives silently dropped
- **Brainstorming (line 40):** "Multi-tenant: every entity carries `domainId`; reads must go through `findEntitySafe()` with `isEntityReadDenied()` gate."
- **PRD:** Carries only the wire-level consequence (`X-Domain-Id` in FR-TM-021, NFR-TM-004). The actual in-process read-safety primitives (`findEntitySafe`, `isEntityReadDenied`) are never named — even though FR-TM-062 promises to "reuse twins' auth, multi-tenancy, and permission infrastructure as-is."
- **Risk:** Architecture phase may reinvent the gate or assume `X-Domain-Id` alone is sufficient.
- **Suggested fix:** Add one line to FR-TM-062 consequences: "All twins-mcp-initiated reads flow through twins' `findEntitySafe()` / `isEntityReadDenied()` multi-tenant gate; the MCP server never bypasses it."

### G2. DTO conventions (`@Schema`, `RqDTOv1`/`RsDTOv1`, `@RelatedObject`) dropped
- **Brainstorming (line 39):** "DTO conventions are strict (`@Schema`, `RqDTOv1`/`RsDTOv1`/`*DTOv1` suffixes, `@RelatedObject` for resolved UUIDs)."
- **PRD:** FR-TM-040 hybrid response shape depends on "twins DTO field names" and FR-TM-062 promises "no parallel DTO layer" — but the convention set that makes that promise real is never enumerated.
- **Risk:** A future contributor ships a parallel hand-rolled DTO because no rule told them not to.
- **Suggested fix:** Add to Constraints and Guardrails: "`twins-mcp` reuses twins DTO types verbatim (including `@Schema` annotations, `*DTOv1` suffix convention, and `@RelatedObject` UUID resolution); no parallel DTO layer."

### G3. Auth implementation anchors dropped (`ApiUser` thread-local, `@ProtectedBy`, route layout)
- **Brainstorming (line 41):** "Auth: `ApiUser` thread-local; permissions via `@ProtectedBy`; routes under `controller/rest/{priv,pub,auth}`."
- **PRD:** FR-TM-062 promises auth reuse; FR-TM-021 covers M2M token plumbing. The mechanism inside twins (`ApiUser` thread-local + `@ProtectedBy`) is dropped — even though it determines whether in-process module integration (D9 Option B) works at all.
- **Risk:** Architecture must re-derive how `twins-mcp` obtains an `ApiUser` for in-process calls; brainstorming already named the answer.
- **Suggested fix:** Add to FR-TM-062 or Integration section: "`twins-mcp` runs inside the twins Spring context; M2M token exchange materializes an `ApiUser` thread-local, and tool endpoints are gated by `@ProtectedBy` like any twins controller."

### G4. Service-account framing shifted from "per-domain user" to "M2M account" without recorded rationale
- **Brainstorming (line 60):** "Service account: Dedicated view-only user per domain."
- **PRD §3:** Service account = M2M account authenticated via `POST /auth/m2m/token/v1`. D12 records that M2M has no scopes but does NOT record why the brainstorming's "per-domain user" framing was abandoned for an M2M account.
- **Risk:** Minor — operational guidance in README may not convey that one M2M account per domain is still the intended provisioning unit.
- **Suggested fix:** Add to §3 or FR-TM-020: "Operators provision one M2M service account per domain; the account carries read-only `@ProtectedBy` grants and its credentials are bound to a single `TWINS_DOMAIN_ID`."

### G5. `TWINS_MCP_TRANSPORT` env var retired silently
- **Brainstorming (line 55):** Names `TWINS_MCP_TRANSPORT` env var as the transport selector.
- **PRD:** D7/D19 dropped multi-transport for v1; PRD FR-TM-020 lists three env vars, none of which is `TWINS_MCP_TRANSPORT`. No decision records that this env var was retired (vs. reserved for v2).
- **Risk:** Trivial — but a reader cross-referencing the brainstorming sees a vanishing variable.
- **Suggested fix:** Add a one-line note to FR-TM-022 Out-of-Scope: "The `TWINS_MCP_TRANSPORT` env var is reserved for v2 (Streamable HTTP); v1 hard-codes stdio."

## Non-Gaps (Confirmed Faithful)

- All 9 scope-table dimensions present or superseded by a recorded decision.
- All 3 brainstorming open questions (which endpoints expose TwinClasses / fields / relations) resolved in FR-TM-001, FR-TM-002, FR-TM-003.
- Glossary scope shift (deferred → in v1 as Terminology → as Twins of `TWINS_GLOSSARY`) fully recorded in D5, D15, D30.
- Read-only posture preserved end-to-end.
- "Domain guide" qualitative intent preserved in §1 Vision and §2.1 JTBD (the "what it should feel like" survived the FR translation).
