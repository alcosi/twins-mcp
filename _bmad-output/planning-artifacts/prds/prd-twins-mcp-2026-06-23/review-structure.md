# Structural Review — PRD twins-mcp

Verdict: **minor adjustments**. The PRD is structurally sound and feeds downstream workflows cleanly. No cuts of load-bearing content needed; the findings below are reorganization, dedup, and re-balancing of section sizes.

## Findings

### F1. `§6.2 Out of Scope for MVP` materially overlaps `§5 Non-Goals` and the per-FR `Out of Scope` blocks

- §5 lists "Not exposing TwinFactory" / "Not a write surface" / "Not multi-domain".
- §6.2 re-states the same items at greater length (TwinFactory, multi-domain, transition prediction).
- Each FR also has its own `Out of Scope` mini-section.

Three-way restatement of the same exclusions. Recommend: keep §5 as the single normative non-goals list; reduce §6.2 to a one-line "see §5" plus only the v2 *forward-looking* items (HTTP, OAuth, telemetry, `.well-known/mcp`) that are not pure non-goals. FR-level `Out of Scope` blocks should defer to §5 for items already covered there and only carry FR-specific exclusions.

### F2. Cross-cutting section ordering breaks the logical reading flow

After §9 Assumptions Index, the document continues with `Cross-Cutting NFRs`, `Constraints and Guardrails`, `Versioning`, `Language/Runtime`, `Integration`, `Risk` — all unnumbered top-level headers. Two issues:
- They sit *after* §8/§9, so a reader scanning the numbered spine misses them.
- No `## 10+` numbering, so downstream artifacts (Architecture, Epics) cannot cite them by section.

Recommend: promote to numbered §10 NFRs, §11 Constraints & Guardrails, §12 Risks, etc. Move them above §8 Open Questions (open questions are conventionally last).

### F3. Glossary §3 mixes twins-anchored terms (kept) with PRD-only terms that are unused

- `MCP transport`, `MCP session`, `Domain Catalog`, `Terminology` — used downstream, earn their place.
- `MCP tool` definition is needed but `MCP client` is self-evident given the audience (twins maintainer + OSS contributors).
- `Smithery` is used 5x in body but never defined in §3 — add a one-liner.

Minor; trim two entries, add one.

### F4. FR-TM-040 / FR-TM-041 / FR-TM-042 (§4.4) — response-shape contract is over-explained relative to its complexity

§4.4 is conceptually simple (hybrid shape + cursor pagination + sanitizer) but is given the same depth as the four Domain Catalog tools. Consider compressing the prose around each consequence list. Not a cut — a re-balance.

### F5. FR-TM-003 `pageSize > 50` cap vs FR-TM-041 general `pageSize ≤ 100` cap

Not a contradiction (FR-TM-003 explicitly overrides for one endpoint) but the override is buried mid-bullet. Recommend hoisting the override to the top of FR-TM-003's consequences so reviewers don't miss the divergence from §4.4.

## Quick wins (not blocking)

- `Decision-log D-references (D7, D8, D11, D13, D17–D20, D23, D24)` appear 14x — consider a single `## Decisions Referenced` mini-index near §9 so readers can trace without grep.
- §2.3 UJ-TM-1 climax/resolution is the only narrative in the doc; consider whether SM-TM-* already covers it and the journey can be cut to one paragraph.
