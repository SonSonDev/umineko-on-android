# Brief
- **Goal / what it does**: Android (arm64) port of *Umineko no Naku Koro ni* (Steam / MangaGamer, episodes 1–4) on
  the 07th-Mod `ponscripter-fork` engine, with a native touch UI. Goal stated by the developer: "the cleanest possible
  mobile adaptation, best suited to a phone in **portrait** mode".
- **Audience & what a good day looks like**: the developer (daily phone) and a few friends/contributors who own the
  game. A good day: read for an hour one-handed in portrait, no crash, saves intact, UI never in the way.
- **Ambition / tier**: T1 personal tool, source repo shared on GitHub (one external contributor so far). Not
  published to any store.
- **Scale**: 1 Activity, ~1.2k lines Java UI, ~150 lines JNI bridge, engine patched under `#ifdef ANDROID_PORT`.
  Game data ~9 GB outside the APK. Lifetime: as long as the developer plays/maintains it.
- **Constraints**: personal use only; no game data and no APK containing it ever in the repo; engine is GPLv2;
  phone is also a daily phone (see CLAUDE.md Safety); arm64 only; minSdk 29.
- **Non-goals**: store release, redistribution of game data, iOS, tablets/foldables tuning, replacing the engine,
  alternative layouts (until UI is migrated to Compose), analytics/crash reporting.

## Risks & assumptions to validate
| Assumption / risk | Impact if wrong | How to validate | Status |
|---|---|---|---|
| Portrait text panel parsing (control codes `0x0e/0x0f`) is correct on all episodes | Garbled or mis-coloured text | Play ep. 2–4 and NVL mode; unit-test `formatPage` | not validated (formatting commit never tested on phone per decisions log) |
| Episodes 2–4, NVL mode, landscape work | Missing features | Manual playthrough checklist | open |
| Movie rendering in cropped area (`androidSetFullLogical`) | Stretched videos | Visual check | open |
| Engine save-on-focus-loss keeps saves safe | Lost progress | Test kill/restore | open |
| Build with AGP 9.4.1 / Gradle 9.6.0 (uncommitted bump) works with NDK 28 / CMake 3.22 | Cannot build | `./gradlew assembleDebug` | not verified |
| Release build signed with debug key | Can't publish | Accepted (personal use) | accepted |
