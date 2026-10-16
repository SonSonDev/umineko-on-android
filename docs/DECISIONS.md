# Decision log
One entry per decision, newest first. Earlier history (2026-10-09 → 10-12) is in `02-decisions.md` (kept as is).

## 2026-10-16 — Kickoff of the existing project (reverse-engineered docs)
- Decision: tier **T1** (personal tool, shared GitHub repo, no store). Why: asked to the developer; one maintainer.
- Decision: goal = best possible **portrait** mobile adaptation. UI direction "Livre-objet discret"; see `DESIGN.md`.
- Decision: **themes by tokens** (1 shipped, 1 later), single portrait layout. Rejected: alternative layouts now
  (needs Compose first), single fixed style (hard-coded colours stay scattered).
- Decision: rule "every frequent action has a visible button; gestures are shortcuts only".
- Decision (approved 2026-10-16): split `MainActivity`, extract pure logic + JUnit tests, then migrate
  screen by screen to Kotlin/Compose. Rejected: big-bang Compose rewrite (no safety net), staying a 1185-line class.
- Inferred earlier decisions (from code, marked inferred): Java UI over SDLActivity; engine hooks via JNI; arm64 only;
  release signed with debug key; stdout redirected to logcat.
- Sources: `docs/01-recherche.md`, `docs/02-decisions.md`, design research in `DESIGN.md` (2026-10-16).

## Pending
- Kotlin/Compose migration: confirm after the first split step shows the cost.
- Gradle 9.6.0 / AGP 9.4.1 (uncommitted bump): check against official releases before committing.
