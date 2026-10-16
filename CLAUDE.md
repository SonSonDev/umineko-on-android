# CLAUDE.md — umineko-and (Android port of Umineko, C++ engine + Java UI)

## Context
- Developer: experienced web front-end (Vue, TypeScript), new to Android and Kotlin. When introducing an
  Android-specific concept (Activity, lifecycle, JNI, recomposition…), explain it in one or two lines with a web analogy.
- Talk to the developer in **French**. Code, identifiers, comments, docs and commit messages in **English**.
  (Existing `README.md` and `docs/01-02` are in French; leave them, write new docs in English.)
- Target: Android arm64, minSdk 29, personal use, shared source repo (no game data, no distributed APK).
  Test phone: Sony H8324 (Android 10) — also the daily phone, see Safety.
- Tier: **T1** (see `docs/DECISIONS.md`). Core goal: the best possible **portrait** mobile adaptation.

## Stack (from the repo, 2026-10-16 — NOT re-verified online; re-check before upgrading)
- Java UI over `SDLActivity`; engine C++14 (`engine/`, 07th-Mod ponscripter-fork) built with NDK 28.2.13676358 and
  CMake 3.22.1; SDL2 2.32.10, SDL2_image 2.8.8, SDL2_mixer 2.8.1, FreeType 2.13.3, libogg 1.3.5, libvorbis 1.3.7.
- Gradle wrapper 9.6.0 + AGP 9.4.1 (uncommitted bump, see QUESTIONS), compileSdk/targetSdk 35, ABI arm64-v8a only.
- **Library policy**: anything not listed needs approval. Before adding: latest stable, maintenance, publisher, licence;
  prefer AndroidX/Kotlin/Google; say what was added and why; log it in DECISIONS.md.
- **Version freshness**: at the start of a session touching dependencies and at each milestone, compare against current
  stable releases and report what is behind. Never claim "verified" without checking online that session.
- Planned (not started, needs approval): Kotlin + Compose for the native UI, screen by screen.

## Project documentation
- @docs/BRIEF.md — goal, ambition, constraints (the why)
- @docs/SPEC.md — features, behaviour, status
- `docs/DESIGN.md` — visual direction, tokens, rules (read it before any UI work; run the `design-ui` skill in apply mode)
- `docs/ARCHITECTURE.md` — code map and data flows (read when touching structure)
- `docs/DECISIONS.md` — decision log, newest first (`docs/02-decisions.md` = pre-kickoff history)
- `docs/QUESTIONS.md` — doubtful behaviours awaiting an answer
- **Docs are always in sync with the code, without being asked, in the same change:**
  behaviour → SPEC · structure → ARCHITECTURE · any technical/product choice (even in discussion) → DECISIONS.
  End each task by stating which docs were updated (or none). If doc and code disagree, say so.
- Context budget: only BRIEF and SPEC are `@`-imported; each doc < ~400 lines; archive old decisions.

## Architecture rules
- Engine changes only under `#ifdef ANDROID_PORT`; keep `docs/engine-changes-vs-upstream.patch` current.
- The engine decides when Android draws; Android only draws and reports taps back (`nativePressButton`). No game logic
  in the UI layer.
- JNI callbacks arrive on the SDL thread: post to the UI thread, coalesce updates; never block the SDL thread.
- Native UI: one class per screen, parsing/formatting/classification as **pure functions** (testable), colours/fonts/
  sizes only from the theme tokens (`ui/theme`) — no `Color.rgb(...)` in screens. Split files > ~400 lines.
- Vendored `org/libsdl/app/*` and `third_party/` are not edited by hand.

## UX & design rules (details in `docs/DESIGN.md`)
- Portrait first; immersion: biggest scene, discreet controls reachable by thumb. Landscape = engine draws all.
- Every frequent action has a **visible** button; gestures are shortcuts only.
- Touch targets ≥ 48 dp, spacing ≥ 8 dp, text ≥ 16 sp, contrast WCAG AA, respect system font scale, insets/cutout.
- Loading / empty / error / content states designed for each native screen.
- No Material/generic app look. Themes swap through tokens only.

## Quality gates (a task is not done until these pass; say which were run)
- `./gradlew testDebugUnitTest` and `./gradlew lintDebug` (lint currently non-blocking — see QUESTIONS); `assembleDebug` builds.
- New pure logic comes with JUnit tests; bug fixes start with a failing test.
- UI/engine changes are verified on the phone or emulator (screenshot) — say explicitly when not verified.

## Data safety & security
- **The game data (~9 GB) and saves are precious**: never delete `files/game` or `files/save`; uninstalling the app
  deletes both. Release is signed with the debug key so it installs over debug without uninstall.
- No game data, APK, keystore, `local.properties` or secrets in Git. Respect the GPLv2 of `engine/`.

## Safety (device & environment)
- **Never uninstall the app, clear its data or run `connectedAndroidTest` on the developer's phone.** Install with
  `adb -s <serial> install -r` only; raise `versionCode` when a reinstall is needed. Ask before touching the phone.
- Destructive or hard-to-reverse actions (delete data, force-push, reset, uninstall) are confirmed first.

## Working agreement
- Ask (via `AskUserQuestion`, one question at a time, recommendation first) when it is the developer's call; decide
  and say so when there is a conventional answer.
- Small reviewable steps; one logical change per commit, imperative English message; commit only when asked.
- Check whether the platform or an approved library already does it before building.
- Milestone check (`/project-checkpoint`, each release or ~20 commits): stale deps, dead code, files > 400 lines,
  docs size, untested logic, TODOs — report, don't refactor unasked.
- Prefer deleting code to commenting it out. Comments explain *why*.

## Commands (Windows)
- Build debug: `./gradlew assembleDebug` · release: `./gradlew assembleRelease`
- Unit tests: `./gradlew testDebugUnitTest` · Lint: `./gradlew lintDebug`
- Deploy to the phone (build + install + data): `.\tools\deploy.ps1 -SkipPush` (code only) — see `README.md`.
- Logs: `adb logcat -s Ponscr UminekoText`
