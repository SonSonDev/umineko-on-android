# Recherche : portage d'Umineko (Steam 1-4) sur Android

Date : 2026-10-09 — Usage personnel, jeu acheté sur Steam.

## 1. Constat sur les fichiers du jeu
Dossier : `E:\SteamLibrary\steamapps\common\Umineko`
- `0.utf` (script UTF-8), `arc*.nsa` (archives), `BGM/ SE/ ME/ movie/ fonts/`, `Umineko1to4.exe`
- Le moteur de la version Steam (MangaGamer) est **Ponscripter**, pas ONScripter pur :
  UTF-8, polices proportionnelles. Les effets personnalisés de NScripter/ONScripter
  (ex. la pluie animée) n'y existent pas.
- Fork de référence : **07th-Mod/ponscripter-fork** (SDL2, GPLv2, CMake/configure).
  Il contient un mode `@umineko` (échelle de police + `getspsize`), typiquement
  `modew540@2x@umineko` pour une sortie 1920x1080.
- **Aucun support Android/ARM** documenté dans ponscripter-fork (INSTALL.md = desktop uniquement).
  Dépendances : SDL2, SDL2_image, SDL2_mixer, smpeg2, bzip2, Freetype. Le SDK Steamworks est optionnel (`--steam`).

## 2. Candidats pour le moteur Android

| Option | Moteur | Android | Compatibilité avec les données Steam | Effort |
|---|---|---|---|---|
| A | Portage de **ponscripter-fork** (le moteur d'origine) | À créer (NDK + SDL2) | Maximale (mêmes scripts, mêmes archives) | Élevé |
| B | **ONScripter-Jh / OnscripterYuri / ONScripter Plus** | Existe (APK, Play Store) | Incertaine : pas de garantie sur les extensions Ponscripter (UTF-8, polices proportionnelles) | Faible à moyen, risque de patchs |
| C | **Umineko Project** (`onscripter-new`, base ONScripter-RU) | Natif (Android 11+, arm64/x86_64, SDL3 + Vulkan) | Ne lit PAS les données Steam : version PS3-ifiée, assets extraits avec un mot de passe de 27 chiffres (PC + PS3) | Très faible, mais ce n'est pas un portage de votre version |

Pistes secondaires :
- Kirikiroid2 / krkrsdl2 : pour moteur Kirikiri, hors sujet ici.
- La limite de 2 Go d'un APK impose des données externes (cas du port Android de Higurashi). Ici ~1,9 Go de données : on les garde hors de l'APK.

## 3. Méthodologie retenue par les ports expérimentés
Tirée de `onscripter-new/Resources/Docs/Android.md` et des ports Higurashi / Umineko Project :
1. **Séparer moteur et données** : l'APK ne contient que le moteur, le jeu est fourni à part.
2. **Moteur en C++ compilé via NDK, interface en Java/Kotlin** (projet Gradle) : l'activité SDL fournit
   la fenêtre, le tactile et l'audio, le reste est le code moteur inchangé.
3. **Stockage** : deux routes.
   - Dossier dédié à l'appli : `/sdcard/Android/data/<package>/files/` (aucune permission, simple via `adb push`).
   - Dossier choisi par l'utilisateur : `ACTION_OPEN_DOCUMENT_TREE` + `MANAGE_EXTERNAL_STORAGE`.
   Pour un usage perso, la route dédiée suffit.
4. **Sauvegardes** dans un dossier séparé du jeu (`SaveData/<id>`), pour ne pas les perdre à la mise à jour.
5. **ABI** : arm64-v8a suffit sur les téléphones récents (+ x86_64 pour l'émulateur).
6. **Boucle de dev rapide** : compiler le moteur une fois, ne refaire que le Java/Kotlin ensuite. Le premier build natif peut être long (> 1 h).
7. Outils vus en pratique : Android Studio, NDK récent, CMake/Ninja, JDK 17, MSYS2 sous Windows, `adb`.

## 4. UX mobile recommandée pour un visual novel
Principes généraux de conception tactile (Smashing, Material) + conventions des ports existants :
- Zones tactiles >= 48 px (≈ 9 mm), avec une zone cliquable plus large que l'icône.
- **Toucher n'importe où = avancer le texte** ; **appui long ou bouton dédié = menu** (comportement des ports ONScripter).
- Actions fréquentes (Auto, Skip, Backlog, Sauvegarde rapide) dans la moitié basse de l'écran, atteignables au pouce.
- Gestes : balayage vers le haut = backlog, vers le bas = masquer la fenêtre de texte (à valider selon le moteur).
- Ratio 16:9 conservé (pas d'étirement), barres noires ou fond neutre, gestion de l'encoche et de la barre de navigation.
- Écran maintenu allumé, pause automatique de l'audio à la mise en arrière-plan, sauvegarde automatique à la sortie.
- Réglages : vitesse du texte, volume BGM/SE/voix, taille de la fenêtre de texte.
- Retour Android = menu de pause (pas de fermeture directe).

## 5. Risques
- Données Steam au format Ponscripter : le choix du moteur est la décision critique (voir `02-decisions.md`).
- Vidéos (`movie/`) : codecs et décodage matériel à vérifier sur Android.
- Mémoire : textures 1080p et gros `.nsa`.
- Licences : GPLv2 pour les moteurs. Usage perso, aucune redistribution.

## Sources
- https://github.com/07th-mod/ponscripter-fork
- https://github.com/timftw21/onscripter-new (doc Android : `Resources/Docs/Android.md`)
- https://github.com/jh10001/ONScripter-Jh
- https://github.com/matthewn4444/onscripter-plus-android
- https://darwin07tachibana.blogspot.com/2020/09/higurashi-no-naku-koro-ni-kai-android.html
- https://www.kaisernet.org/onscripter/others.htm
- https://zh.moegirl.org.cn/Galgame%E6%A8%A1%E6%8B%9F%E5%99%A8

Limites de la recherche : les forums japonais, coréens et chinois n'ont rien donné de spécifique à Ponscripter sur Android.
Aucun portage Android existant de ponscripter-fork n'a été trouvé.
