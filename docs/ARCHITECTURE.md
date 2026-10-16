# Architecture — code map
Updated whenever a structural element is added, moved or removed. Principles live in CLAUDE.md.
Marked *(inferred)* = reconstructed from the code and the decisions log.

## Layout
```
app/                         Android app (Gradle, Java, CMake)
  src/main/java/dev/umineko/android/MainActivity.java   SDLActivity subclass + ALL native UI (~1185 lines)
  src/main/java/org/libsdl/app/                         SDL2 Java glue (vendored, do not edit)
  src/main/cpp/CMakeLists.txt                           builds engine + SDL libs into libmain.so
  src/main/cpp/android_bridge.cpp                       JNI: nativeSetPortraitText, nativePressButton (+ engine→Java calls)
  src/main/cpp/android_log.cpp                          stdout/stderr → logcat (tag Ponscr)
engine/                      07th-Mod/ponscripter-fork (commit ba457e4), patched under #ifdef ANDROID_PORT
third_party/                 SDL2, SDL2_image, SDL2_mixer, FreeType, libogg, libvorbis (modern versions)
tools/deploy.ps1             build + install + push game data
docs/                        this documentation; 01-recherche.md and 02-decisions.md are the pre-kickoff history;
                             engine-changes-vs-upstream.patch = diff of our engine changes
```

## Components
- **MainActivity** (Java): launches SDL with args `-r <files>/game/ -s <files>/save/`; creates the data folder
  itself (permissions). Receives engine callbacks `onGamePage`, `onGameButtons`, `onGameChoices`, `onGameNote`
  (called from the SDL thread, posted to the UI thread; page updates coalesced per frame). Builds the portrait layout:
  scene surface on top, text panel below, bar Auto/Skip/Menu, native history, menu/choices/note overlays.
  Smell: one 1185-line class mixes layout, formatting, menu classification, labels translation, preferences.
- **Engine hooks** (C++): `nativeStoryText()`, `nativeMenuActive()`, `nativeChoicesActive()`, `androidPublishButtons()`,
  `androidSendPage()`, `androidViewRect`/`g_crop_x` (scene crop), `androidMarkStyle`/`androidMarkSoftBreak`.
- **Page wire format**: engine text + control bytes (`0x10–0x1f` formatting codes stripped; `0x0e RRGGBB style size%`
  and `0x0f` soft break added by us). Buttons: lines `id TAB image TAB file [TAB x TAB y]`.

## Data flow
```
script/engine (SDL thread) --JNI--> MainActivity.onGame* --runOnUiThread--> Views
Views (tap) --nativePressButton(id)--> SDL user event --> engine (same effect as a click)
```
Engine decides *when* Android draws (story page, button screen, select); Android only reports taps back.

## Navigation
No Android navigation: one Activity; screens are overlays chosen by the engine state (story / menu / choices / note).

## Conventions
- Engine changes only under `#ifdef ANDROID_PORT`; keep `engine-changes-vs-upstream.patch` current.
- Native UI strings are currently hard-coded English in Java (to move to `strings.xml`, see QUESTIONS).
- Target structure (planned, not started): `ui/theme` (tokens), one class per screen, pure formatting/classification
  logic extracted and unit-tested.
