# Open questions
Ask, never decide silently. Remove once answered (record the answer in SPEC / DECISIONS).
- [ ] `02-decisions.md` says AGP 8.13.2 / Gradle 9.3.1; the working tree has AGP 9.4.1 / Gradle 9.6.0 uncommitted.
  Which is intended? Recommendation: keep the bump only after a clean `assembleDebug`, and log it.
- [ ] README says "Android 10 or more"; `02-decisions.md` once said minSdk 30; code says 29. Code wins (29) — fix the doc?
- [ ] Is the quick-save button (F10) wanted, and what should it do (slot 1? a dedicated "quick" slot)? The engine has no quick-save.
- [ ] `lint { abortOnError false }` contradicts the T1 gate (lint green, NewApi error). Turn lint into a gate?
- [ ] R8 is off in release (`minifyEnabled false`). Acceptable at T1 (nothing to protect), or enable?
- [ ] Native UI strings are hard-coded in Java. Move to `strings.xml` (French translation useful?) or keep English-only?
