# Prose Review — PRD twins-mcp

**Verdict: minor polish**

The PRD is well-written for a launch-stakes OSS deliverable. Grammar is solid, terminology is consistent, and tone is appropriate. A handful of small issues — mostly Russian-English interference on articles and one ambiguous reference — would benefit from a polish pass. Nothing material blocks understanding.

## Top Findings

### 1. FR-TM-003 Out of Scope — ambiguous pronoun
**Location:** §4.1 FR-TM-003, Out of Scope, line 126
**Issue:** "agent can chain `describe_class` calls itself" reads awkwardly; "itself" is the wrong reflexive for an agent subject and the intent is unclear.
**Fix:** "the agent can chain `describe_class` calls on its own" or "chaining is left to the agent via successive `describe_class` calls."

### 2. Glossary intro — missing article
**Location:** §4.2 Terminology Tools, line 147
**Issue:** "exposed by the `TWINS_GLOSSARY` class" is fine, but the prior clause "as a set of Twins of TwinClass key `TWINS_GLOSSARY`" drops the article — typical Russian-English calque.
**Fix:** "as a set of Twins of the `TWINS_GLOSSARY` TwinClass" or "of TwinClass key `TWINS_GLOSSARY`" → "whose TwinClass key is `TWINS_GLOSSARY`."

### 3. "Terminology" capability description — minor redundancy
**Location:** §3 Glossary, line 70 and §1 Vision, line 27
**Issue:** Two near-identical definitions of "Terminology" exist; §3 says it exposes `docs/glossary.md`, but §1 and §4.2 say the glossary lives as Twins of class `TWINS_GLOSSARY` inside the domain. The §3 wording contradicts §4.2.
**Fix:** Align §3's "Terminology" entry with §4.2 — "v1 capability exposing the twins glossary (stored in-domain as Twins of class `TWINS_GLOSSARY`)."

### 4. NFR-TM-001 — units clarity
**Location:** Cross-Cutting NFRs, line 400
**Issue:** "glossary tools p95 ≤ 500ms (no network)" — the parenthetical is ambiguous (no network call? no network dependency?).
**Fix:** "(no remote REST call — glossary is served from in-process state)" or rephrase to "(local-only, no REST round-trip)."

### 5. Inconsistent casing of "twins"
**Location:** Throughout (§0, §1, §4.6, FR-TM-062, NFR-TM-007)
**Issue:** The product is consistently "twins" (lowercase) per the repo convention, but possessive forms alternate: "twins' own documentation" (§0), "twins' auth, multi-tenancy" (§1), "twins': REST client" (FR-TM-062). The colon in the FR-TM-062 line is a typo.
**Fix:** Standardize on "twins'" (no colon). Fix FR-TM-062 to "Module reuses twins' REST client configuration, M2M auth client, …"

## Lower-priority notes (not blocking)
- §6.2 "emotionally load-bearing" (line 359) is informal for a PRD; "strategically important to the user's roadmap" is more measured.
- §4.4 FR-TM-040: "avoid context bloat" is fine but "to avoid context-window bloat" reads more precisely.
- §2.1 "without being a twins expert" — consider "without twins expertise" for parallelism.

## Overall
The PRD reads cleanly. Author's English is strong; only the article drops in §4.2 and the §3 vs §4.2 contradiction rise above nitpick level.
