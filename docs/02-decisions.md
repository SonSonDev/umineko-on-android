# Décisions et plan

## Décisions (2026-10-09)
| Sujet | Choix | Raison |
|---|---|---|
| Moteur | Porter **07th-Mod/ponscripter-fork** sur Android | Compatibilité maximale avec les données Steam |
| Appareil | Téléphone physique ARM64 | Cible `arm64-v8a`, test via adb |
| UX | Minimal puis itératif | Toucher = avancer, appui long = menu, retour = pause |
| Données | Hors APK, `/sdcard/Android/data/<package>/files/` | APK limité, ~1,9 Go de données |
| Usage | Personnel, pas de redistribution | Licence GPLv2 du moteur, jeu acheté |

## Architecture cible
```
umineko-and/
  docs/                 documentation
  engine/               sous-module ou copie de ponscripter-fork (patché Android)
  app/                  projet Android Studio (Gradle, Java/Kotlin)
    src/main/java/...   SDLActivity + lanceur (choix du dossier de données)
    src/main/cpp/       CMakeLists.txt qui compile le moteur + SDL2 + libs
  third_party/          SDL2, SDL2_image, SDL2_mixer, freetype, bzip2, smpeg2
```
Principe : moteur C++ via NDK/CMake, appelé par une SDLActivity. Données et sauvegardes séparées.

## Plan par jalons
1. **Environnement** : Android Studio, SDK, NDK, CMake, Git, adb, téléphone en débogage USB.
2. **Squelette** : projet SDL2 Android qui affiche un écran de test sur le téléphone.
3. **Compiler les dépendances** (SDL2_image, SDL2_mixer, Freetype, bzip2, smpeg2) pour arm64.
4. **Compiler ponscripter-fork** pour Android, sans Steamworks. Corriger les chemins et les E/S.
5. **Premier lancement** : pousser les données par `adb push`, lire `0.utf` et les `.nsa`, afficher le titre.
6. **Mode Umineko** : paramètres `@umineko` pour l'échelle et la résolution.
7. **Tactile** : toucher = clic, appui long = clic droit/menu, bouton retour = pause.
8. **Audio, vidéo, perf** : vérifier la lecture BGM/SE, les films, la mémoire.
9. **Finitions UX** : barre Auto/Skip/Backlog, sauvegarde à la sortie, écran toujours allumé.

## Journal
- 2026-10-09 : recherche terminée, décisions prises.
- 2026-10-09 : jalons 1 à 5 atteints. Le moteur compile pour arm64 et affiche l'écran de choix de langue sur un NX769J (Android 16).

## Choix techniques réalisés
- Gradle 9.3.1, AGP 8.13.2, JBR 25 (JDK de Android Studio), NDK 28.2.13676358, CMake 3.22.1, compileSdk/targetSdk 35, minSdk 30, ABI arm64-v8a uniquement.
- Bibliothèques modernes au lieu de celles du moteur (SDL2 2.0.3 trop vieux) : SDL2 2.32.10, SDL2_image 2.8.8 (backend stb), SDL2_mixer 2.8.1 (Vorbis via stb), FreeType 2.13.3, libogg 1.3.5, libvorbis 1.3.7.
- Gardés depuis `engine/src/extlib` : bzip2 et smpeg2 (vidéos .mpg).
- Moteur : `07th-mod/ponscripter-fork` (dossier `engine/`, commit ba457e4), compilé en C++14 avec `-DLINUX -DUTF8_FILESYSTEM -DANDROID_PORT`, sans routines SIMD x86 (repli C générique).
- Modifications du moteur, toutes sous `#ifdef ANDROID_PORT` :
  - `Ponscripter.cpp` : redirection de stdout/stderr vers logcat (tag `Ponscr`, fichier `app/src/main/cpp/android_log.cpp`).
  - `ScriptHandler.cpp` : message de diagnostic `errno` si le script est introuvable.
  - `smpeg-2.0.0/audio/mpegtoraw.cpp` : suppression d'un `assert` invalide (pointeur comparé à 0).
- Les 6 commandes inconnues (`language`, `errorsave`, `addkinsoku`, `setlayer`, `menu_click_def`, `menu_waveon`) sont des avertissements sans gravité : le fork public ne les implémente pas non plus.

## Piège rencontré : permissions du dossier de données
Si le dossier `/sdcard/Android/data/dev.umineko.android/files/game` est créé par `adb` avant l'appli, il appartient à `shell` et l'appli reçoit `EACCES`.
`MainActivity.onCreate` crée donc le dossier elle-même. Procédure : installer, lancer une fois, puis faire le `adb push`.

## Reste à faire
- Orientation paysage forcée et plein écran immersif (le NX769J a un écran 1116x2480).
- Tactile : toucher = avancer, appui long = menu, retour Android = pause.
- Dossier des sauvegardes : le moteur essaie `~/.Umineko4hdz` (inaccessible sur Android), à rediriger avec `-s`.
- Tester le son, les vidéos et les performances.
- Paramètres du mode `@umineko` pour l'échelle des polices.

## Voix (patch 07th-Mod, 2026-10-09)
- Les données Steam seules n'ont aucune voix. Le patch 07th-Mod « Voice and Graphics » (installateur Windows v1.4.1) ajoute `0.u` (script, lu avant `0.utf` par le moteur), `arc4.nsa` et `arc5.nsa` (51 088 voix .ogg, non compressées) et des images dans `bmp/`, `big/`, `en/`, `textbox/`.
- Poussé sur le téléphone : `0.u`, `arc4.nsa`, `arc5.nsa`, `voice/`, `textbox/` et les 1 490 images du patch absentes des `arc*.nsa` (le reste des dossiers loose est déjà dans les archives).
- Le moteur actuel lit le script et les archives sans modification. Épisode 1 testé : pas de fichier manquant dans logcat.
- Espace libre sur le téléphone après le push : environ 2,7 Go.
- À vérifier à l'oreille : lecture réelle des voix et du volume.

## Interface portrait (2026-10-09)
Principe : la scène est rendue par le moteur (rognée sur les côtés), le texte par Android.
- **Scène** : surface SDL 4:3 pendant l'histoire (16:9 hors histoire). `PonscripterLabel::androidViewRect` (PonscripterLabel.cpp) ne copie qu'une bande de 720 px sur 960 ; la bande est placée pour que chaque personnage (`tachi_info[0..2]`) reste visible à au moins 2/3. Les clics sont décalés de `g_crop_x`.
- **Texte** : en histoire, le moteur ne dessine plus ni la boîte ADV ni le texte ni les curseurs (`nativeStoryText()`, PonscripterLabel_image.cpp). À chaque attente de clic, `clickWait`/`clickNewPage` envoient la page (`current_text_buffer`) + le nom de l'image de boîte (`textbox\normal\NN.png`) à `MainActivity.onGamePage` via JNI (`app/src/main/cpp/android_bridge.cpp`). Le numéro NN donne le personnage (table `SPEAKERS`).
- **Quand le moteur dessine tout** (pas de rognage ni de masquage) : titre, notices, menu clic droit (`event_mode` = WAIT_BUTTON_MODE sans WAIT_TEXTBTN_MODE) et choix (`select_links` non vide). Pendant l'histoire, l'attente de clic donne `event_mode=98` (bit 64 présent).
- **Mode histoire** : actif dès qu'une page est envoyée avec une boîte `textbox`, remis à zéro par `reset` (retour au titre). Requiert ADV (choisi à la première notice).
- **Android** : panneau de texte natif (nom du personnage, taille réglable A−/A+), tap = avancer, balayage vers le haut ou « Histo » = historique natif, barre Auto/Skip/Menu. Paysage : le moteur dessine tout comme avant.
- Le moteur sauvegarde ses variables globales quand l'appli perd le focus (sinon les écrans de notice reviennent à chaque lancement).
- Script de test : navigation adb jusqu'à l'épisode 1 en se calant sur le texte du jeu (voir le journal `UminekoText`).

### Menu natif (clic droit et sous-menus)
- `nativeMenuActive()` : en histoire, `event_mode` = WAIT_BUTTON_MODE sans WAIT_TEXTBTN_MODE, pas de `select`, des boutons actifs. `androidPublishButtons()` (appelée à chaque `rerender`) envoie la liste `id TAB image TAB fichier` à `MainActivity.onGameButtons`. Libellé = texte du sprite (`:s#FFFFFF^~c4~Texte`) ou nom de l'image (`r_btn_clear` → « Masquer le texte »…).
- Les sprites de ces boutons ne sont plus dessinés dans la scène (`isMenuButtonAnim`) ; la scène est redessinée à chaque réactivation du menu, parce que le script les dessine avant l'attente de boutons.
- Un tap sur un bouton natif appelle `nativePressButton(id)` → événement SDL → `androidPressButton` (même effet qu'un clic). « Fermer le menu » = id -1 (clic droit).
- Correctif moteur : `FontsStruct::font` borne le style (le script produit des styles > 7 avec `~c4~`, ce qui plantait la page Options).

### À faire
- Choix `select` natifs, état visible d'Auto/Skip, panoramique animé.
- Menu natif : traduire les libellés des options, afficher les lignes d'état (volume, ADV…) dans le panneau, écrans Sauvegarde/Chargement.
- Vérifier : paysage, mode NVL, épisodes 2 à 4, écrans de sauvegarde/chargement.

## Nouveau téléphone (2026-10-09)
- Sony H8324, Android 10, 1080×2160 : `minSdk` abaissé à 29 (insets avec repli avant Android 11). Taille de texte par défaut = 5,6 % de la largeur en dp (bornée 17–26 sp).
- Données : dossier Steam patché copié en entier (`0.u`, `arc*.nsa`, `BGM`, `ME`, `SE`, `sys_se`, `fonts`, `movie`, `textbox`, `voice`, `bmp`, `big`, `en`), environ 9 Go, `adb push` à ~28 Mo/s.
- « Movie not found » : le patch remplace `movie/umineko_op.mpg` (121 Mo) et `umineko_op3.mpg` ; l'ancienne copie du téléphone avait les vidéos Steam d'origine.
- Pendant une vidéo, la taille logique du rendu repasse en 960×540 (`androidSetFullLogical`) pour ne pas l'étirer dans la zone rognée. Non vérifié visuellement.
- Les interfaces sont en anglais (menu natif, barre, historique).
- Navigation de test : au titre, trois appuis par entrée (survol, puis clic).

## Menus du titre et page d'intro (2026-10-09)
- `nativeMenuActive()` ne dépend plus du mode histoire : tout écran à boutons de sprites (`spbtn`) dont le moteur attend un bouton est affiché par Android. Le moteur envoie `id TAB image TAB fichier TAB x TAB y` ; Android trie par position (haut en bas, gauche à droite), traduit les noms de fichiers (`start` → Start, `web` → Controls, `exit1` → Exit, `ep2_1` → Episode 2…) et met chaque « Unlock » sur la ligne de son élément. « Close menu » n'apparaît qu'en histoire.
- Page d'intro « TIPS MODE » : le texte est incrusté dans `BMP\background\efe\text002/text003.png` (plein cadre 1920×1080). `introNoteId()` reconnaît ces fonds ; le moteur noircit le bas de l'image (à partir de 360/540) et `MainActivity.showNote` affiche le texte en natif, défilable, tap = avancer (le « clic droit » devient le bouton Menu).
- Le panneau ne mélange plus les pages : seul le texte de l'histoire est envoyé.
- Restent dessinés par le moteur : langue, notice, choix ADV, ombre portée, choix de l'opening (écrans `select`/textbtn).

### Ecrans a choix natifs (2026-10-09)
- `nativeChoicesActive()` : tout `select` en attente de bouton. Le moteur n'affiche alors ni le texte ni les boutons de choix ; il envoie l'invite (`current_text_buffer`) et les choix (`select_links`) à `MainActivity.onGameChoices`. L'invite défile, les boutons restent épinglés en bas. Un tap renvoie l'indice du choix (même mécanisme que le menu).
- Couvre : notice, choix ADV/NVL, ombre portée, choix de l'opening. Langue : boutons de sprites (`english_textbutton`…), traduits par le dictionnaire. Les noms inconnus deviennent « Nom du fichier ».

### Sauvegarde / chargement natifs (2026-10-09)
- Les emplacements sont des boutons de sprites `bmp\system\savearea_w.png` (ids 51 à 58). Le moteur joint à chacun le texte des sprites posés dans son cadre (date `Ep1: Oct 9, 10:40 PM`, titre de l'épisode) ; Android affiche « Slot N - date - titre » ou « Slot N - empty », sans le préfixe de style `:s/8,16,1;`.
- Ordre : les emplacements forment une ligne virtuelle classée par numéro (le tri par position les entrelaçait en deux colonnes) ; pages 1 à 5 traduites. Le bouton « Load game » de l'écran Save bascule en mode chargement.
- Les vignettes et textes de sauvegarde restent dessinés dans la scène (visibles en haut).
- Écran de chargement du titre : mêmes emplacements, cadres `bmp\system\loadarea_w.png` (reconnus comme `savearea`). Le jeu ne crée des boutons que pour les emplacements occupés, donc seuls ceux-là sont listés. Chargement testé depuis le titre (emplacement 1).

### Disposition des menus natifs (2026-10-09)
- `showMenu` classe chaque bouton (page, emplacement, action principale, épisode, réglage +/−, lien, pied de page, action courte) et compose l'écran par sections, dans cet ordre : onglets de pages, action principale (large, couleur), réglages à +/−, emplacements (cartes : numéro, date en gras, titre en gris, « Empty » estompé), épisodes (une rangée + pastille Unlock), actions courtes (grille 2 colonnes si libellés ≤ 24 caractères, sinon une colonne), liens discrets, pied de page.
- Pied de page : « Exit » devient « Back » hors du titre ; « Title » devient « Return to title » (rouge) ; « Close menu » n'apparaît que sans « Back ».
- Numéro d'emplacement = (page − 1) × 8 + rang.
- Reste à faire : afficher les valeurs courantes (Music: 40, Voices: 70…) à côté des réglages au lieu de les laisser dans la scène.
