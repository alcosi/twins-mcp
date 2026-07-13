# PRD Quality Review — twins-mcp

## Overall verdict

This PRD exceeds the bar for a launch-stakes OSS technical capability spec. Decisions are honest (D17, D24, D29 overturn earlier drafts in plain sight), the thesis is sharp (collapse domain ramp-up via two read-only capabilities), and done-ness is unusually precise — every FR names the exact REST endpoint, DTO field, and permission grant. Main risks are downstream-shaped: the operator-provisioned `TWINS_GLOSSARY` class (D30) and `showMode` resolution (OQ-PRD-6) push real schema decisions past the PRD, and two of the seven tools (`list_glossary_sections`, `get_glossary_section`) have consequences that defer to OQ-PRD-6/OQ-PRD-7 in a way that an Epic author cannot turn into a story without revisiting the PRD.

## Decision-readiness — strong

The PRD surfaces trade-offs, not smoothed neutralities. §6.2 names what was deferred with explicit plug-in points (`explain_field_permissions`, `predict_transition`). The `[NOTE FOR PM]` in §6.2 ("Permission introspection and transition prediction are emotionally load-bearing — keep them in roadmap visibility") is exactly the kind of callout the rubric asks for: it acknowledges the user's stated vision is being deferred without killing it. The decision log D23 (TwinFactory removed), D24 (Twinflow removed), D29 (`describe_class` split reverted) are visible in the PRD itself via the (Dxx) citations.

### Findings
- **low** Open Questions count vs. stakes (§8) — Seven OQs on a launch-stakes PRD is appropriate, not excessive; none is a phase-blocker. No fix needed; recorded for transparency.

## Substance over theater — strong

No persona theater: two UJs (UJ-TM-1, UJ-TM-2), each with a named protagonist (Nikita, Priya) carrying inline context. JTBD in §2.1 are tight and each maps to an FR. No innovation theater — the "what's novel" framing in §1 Vision is earned: the glossary-as-Twins-of-`TWINS_GLOSSARY` design (D30) is a genuinely non-obvious decision and is presented as such, not as marketing. NFRs (§Cross-Cutting NFRs) carry product-specific thresholds: NFR-TM-001 says p95 ≤ 2s for `list_classes` against ≤ 100 TwinClasses, not "must be fast."

### Findings
- **low** Vision could over-claim generality (§1) — "any MCP-capable agent" reads fine, but FR-TM-050/051 only mention Smithery + `claude mcp add`. *Fix:* add one sentence in §1 acknowledging v1 distribution is Claude-ecosystem-ergonomic-first even though spec compliance is generic.

## Strategic coherence — strong

Clear thesis: collapse twins onboarding by turning an LLM agent into a domain guide via two bundled capabilities (Domain Catalog + Terminology). Feature prioritization follows the thesis — v1 is the read-only catalog and glossary, and the deferred items (permissions, transition prediction, write surface) are exactly the capabilities that don't fit the "guide" framing. Success metrics validate the thesis: SM-TM-2 (install success rate ≥ 70%) and SM-TM-4 (glossary citation rate ≥ 50%) measure engagement quality and grounding, not raw activity. Counter-metrics SM-TM-C1/C2 are well-chosen and explicitly named as "do not optimize" — a genuine tell that the author understood the rubric.

### Findings
- **medium** SM-TM-4 measurement method underspecified (§7) — "measured via sampled review" is vague for a launch-stakes metric. *Fix:* state sampling cadence (e.g., "20 randomly sampled agent answers per month") so the metric is reproducible.

## Done-ness clarity — adequate-to-strong

Most FRs are unusually testable: FR-TM-003 names the DTO (`LinkSearchDTOv1`), the `@Size(max = 50)` cap, the permission grant `LINK_VIEW`, and the directional mapping. FR-TM-001 consequences are crisp (page size ≤ 100, default 20, empty result returns note not error). This is where downstream story creation will lean hardest and the PRD largely delivers.

### Findings
- **high** `list_glossary_sections` (FR-TM-010) and `get_glossary_section` (FR-TM-011) defer their core behavior to OQ-PRD-6/OQ-PRD-7 — An Epic author cannot write a story for "tool aggregates the grouping field's distinct values" without knowing the grouping field exists or how it's filtered. *Fix:* either commit to a recommended `TWINS_GLOSSARY` schema in this PRD (term, definition, section, JPA path, table, notes) and have the tools depend on it, or move both tools to §6.2 v2 and ship v1 with `get_glossary_term` only.
- **medium** FR-TM-002 `showMode` value deferred (OQ-PRD-6) — The "exact value resolved at architecture time" leaves a story-blocking unknown inside the most-used tool. *Fix:* narrow OQ-PRD-6 to "confirm value from `{DETAILED, FULL, ...}` enum in `TwinClassFieldSearchController`" by recording the candidate set now via a one-line codebase grep.
- **low** FR-TM-052 (JSON config block) has no consequences (§4.5) — Just a description; no testable claim about what makes it correct. *Fix:* add "snippet validates against current `claude_desktop_config.json` schema" as a single consequence.

## Scope honesty — strong

§5 Non-Goals is load-bearing: seven explicit non-goals, each defending against a specific silent assumption ("Not a write surface," "Not exposing TwinFactory pipelines (D23)," "Not an HTTP/SSE server"). `[ASSUMPTION]` tags are inline (FR-TM-020, FR-TM-040) and indexed in §9 with roundtrip intact (A1–A5 all appear inline and vice versa). The `[NOTE FOR PM]` in §6.2 is the right shape. De-scoping (Twinflow in D24, TwinFactory in D23, `describe_class` split in D29) is presented honestly with reasons.

### Findings
- **low** Glossary-not-provisioned degradation is in Risk table but not in Non-Goals (§5 vs. §Risk) — Operators may assume the PRD guarantees a glossary. *Fix:* add one Non-Goals line: "Not a glossary-provisioning tool — operator creates `TWINS_GLOSSARY` class manually."

## Downstream usability — strong

Glossary (§3) is present; domain nouns (`TwinClass`, `Twin`, `TwinStatus`, `Link`) reference twins canonical `docs/glossary.md` rather than redefining. FR IDs are contiguous within feature groups (001–004, 010–012, 020–022, 040–042, 050–053, 060–062) and stable per §4 intro. Cross-references resolve (FR-TM-041 referenced from FR-TM-003, FR-TM-010, FR-TM-011). UJs each have a named protagonist. This PRD will source-extract cleanly into Architecture.

### Findings
- **medium** Glossary drift on "Terminology" source (§3 vs. §4.2 vs. D30) — §3 Terminology entry still says glossary lives at `docs/glossary.md` (the bundled-file framing), but §1 Vision, §4.2, §6.1, and D30 say it lives in-domain as `TWINS_GLOSSARY` Twins. *Fix:* update §3 Terminology bullet to: "v1 capability exposing the twins glossary as Twins of TwinClass key `TWINS_GLOSSARY` in the configured domain (D30); superseded the bundled `docs/glossary.md` source mid-draft."
- **low** OQ-PRD numbering skips (§8) — Items are 1–7 inline but cited as OQ-PRD-1 through OQ-PRD-7 in body, with body citations to OQ-PRD-5 (`showMode`, FR-TM-002) and OQ-PRD-6/OQ-PRD-7 (glossary) — the inline index says OQ-PRD-5 = CI scan access, OQ-PRD-6 = `TWINS_GLOSSARY` schema. *Fix:* align: `showMode` is referenced in FR-TM-002 as OQ-PRD-6 but listed in §8 as OQ-PRD-5. Renumber or fix the inline cross-refs.

## Shape fit — strong

Right shape for the product: a capability spec for a technical OSS tool, single-operator install path. Two UJs is appropriate, not over-formalized (a 4+ UJ consumer-product shape would be theater here). Brownfield honesty is excellent: §Integration explicitly references twins' existing OpenAPI at `/api-docs`, M2M token endpoint, `X-Domain-Id` header convention, and `@ProtectedBy` permission model — all verified against the codebase per D6, D11, D12, D24–D27. The module-inside-twins decision (D18, FR-TM-060–062) is the right call for this product and well-justified.

### Findings
- None.

## Mechanical notes

- **Glossary drift**: §3 "Terminology" entry contradicts §1/§4.2/D30 on glossary source (see Downstream usability finding above). Highest-priority mechanical fix.
- **ID continuity**: FR numbering preserved through D23/D27/D29 deletions and reverts per author's stated convention; no gaps that block downstream.
- **Cross-refs**: OQ-PRD-5/OQ-PRD-6 mismatch between body citations and §8 index (see Downstream usability finding).
- **Assumptions Index roundtrip**: A1–A5 all appear inline; no orphan inline tags. Clean.
- **UJ protagonist naming**: Both UJs name the protagonist inline (Nikita, Priya) with role context. Clean.
- **Required sections**: All launch-stakes sections present (Vision, Non-Goals, MVP scope, Success Metrics with counter-metrics, Open Questions, Risk table, NFRs, Constraints, Versioning).
