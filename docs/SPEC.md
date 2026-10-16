# Product specification
Source of truth for scope and behaviour. Status: `planned` · `in progress` · `done` · `dropped`.
Statuses marked *(inferred)* come from the decisions log, not from a fresh test.

## Overview
Runs the unmodified Steam game (with the 07th-Mod Voice and Graphics patch) on Android. The engine draws the scene;
in **portrait** Android draws the text, menus, choices and save screens natively. In **landscape** the engine draws
everything. Data lives in `/sdcard/Android/data/dev.umineko.android/files/{game,save}`, copied with `tools/deploy.ps1`.

## Out of scope
Distribution of game data or APK, store release, iOS, tablets, x86 devices, analytics, cloud sync.

## F1 — Engine port (arm64)
Status: `done` *(inferred)*. Engine, SDL2 2.32, SDL2_image, SDL2_mixer, FreeType, ogg/vorbis built with the NDK.
Unknown script commands (`language`, `errorsave`, `addkinsoku`, `setlayer`, `menu_click_def`, `menu_waveon`) only warn.

## F2 — Data deployment
Status: `done`. `tools/deploy.ps1` builds, installs, creates the data folder through the app (permissions), pushes data.

## F3 — Portrait story view
Status: `done` (formatting `in progress`: colours/italic/bold/soft-breaks not yet tested on device).
- Scene cropped to 4:3 band keeping characters ≥ 2/3 visible; native text panel with speaker name, size A−/A+,
  tap = advance, swipe up or "Histo" = native history, bar Auto / Skip / Menu.
- Story mode requires ADV (chosen at first notice).

## F4 — Native menus
Status: `done`. Title, episode choice, options, TIPS intro page (scrollable note), right-click menu as sections
(page tabs, main action, +/- settings, slots, episodes, short actions, links, footer). Labels translated to English
by a dictionary; unknown names fall back to the file name.
Open: show current values (Music 40, Voices 70…) next to settings; translate remaining option labels.

## F5 — Native choices
Status: `done`. Every `select` (notice, ADV/NVL, drop shadow, opening choice): prompt scrolls, buttons pinned at bottom.

## F6 — Native save / load
Status: `done`. Slots as cards ("Slot N – date – title" / "empty"), pages 1–5, title-screen load (occupied slots only).
Thumbnails remain drawn by the engine in the scene.

## F7 — Landscape
Status: `in progress` (engine draws all; not recently verified).

## F8 — Voices, BGM, movies
Status: `in progress`. Voices present with the patch; audible playback and volume "to check by ear"; movies need the
patched `.mpg`; rendering of movies in the cropped area unverified.

## F9 — Design system and themes
Status: `planned`. Tokens in one `ui/theme` place, theme "Livre-objet", second theme later. See `DESIGN.md`.

## F10 — Visible frequent controls
Status: `planned`. Auto / Skip / History / quick save / Menu always have a visible, thumb-reachable button
(dimmed at rest). Quick save does not exist yet.

## F11 — Release variant
Status: `done`. Signed with the debug key to install over debug without wiping data. R8 off.

## Open questions
See `QUESTIONS.md`.
