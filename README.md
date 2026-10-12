# umineko-and

Portage Android (arm64) de **Umineko no Naku Koro ni** (version Steam / MangaGamer, épisodes 1 à 4), basé sur le moteur
[07th-Mod/ponscripter-fork](https://github.com/07th-mod/ponscripter-fork) avec une interface tactile native (texte,
menus, choix, sauvegardes).

> **Ce dépôt ne contient aucune donnée du jeu.** Il faut posséder le jeu sur Steam. Les données ne sont jamais
> dans l'APK : elles sont copiées séparément sur le téléphone. Usage personnel uniquement.

## Ce qui est installé

| Où | Quoi | Taille |
|---|---|---|
| APK (`dev.umineko.android`) | Moteur C++ compilé (`libmain.so`), SDL2, SDL2_image, SDL2_mixer, interface Java. Aucune donnée du jeu. | quelques dizaines de Mo |
| `/sdcard/Android/data/dev.umineko.android/files/game/` | Copie du dossier Steam du jeu (scripts, `arc*.nsa`, BGM, voix, vidéos…) | ~9 Go avec le patch voix |
| `/sdcard/Android/data/dev.umineko.android/files/save/` | Sauvegardes du jeu | quelques Mo |

Le dossier `game/` est lu par le moteur (`-r`), `save/` est son dossier de sauvegarde (`-s`). Désinstaller l'appli
supprime ces deux dossiers.

## Où est le jeu sur le PC

Le jeu est un dossier Steam, par défaut :

```
C:\Program Files (x86)\Steam\steamapps\common\Umineko
```

(ou dans une autre bibliothèque Steam, par exemple `E:\SteamLibrary\steamapps\common\Umineko`). Pour le retrouver :
Steam → clic droit sur le jeu → Gérer → Parcourir les fichiers locaux. Ce dossier doit contenir `0.utf` (ou `0.u`
après le patch), `arc.nsa`, `arc1.nsa`… `BGM/`, `movie/`.

Ce dossier est celui que l'on passe au script avec `-GameDir`. Rien n'est écrit en dur dans le projet.

### Voix et graphismes (recommandé)

Les données Steam seules n'ont aucune voix. Installer d'abord le patch **Voice and Graphics** de
[07th-Mod](https://07th-mod.com/) (installateur Windows) sur le dossier Steam : il ajoute `0.u`, `arc4.nsa`, `arc5.nsa`
(voix) et des images. Le moteur lit `0.u` avant `0.utf`.

Configuration testée : patch 07th-Mod v1.4.1 (script v1.0.37), options **Console BGM** et **PS3 sprites**,
traductions English/Japanese. Sans le patch, le moteur démarre (c'était le cas aux premiers jalons) mais l'interface
native n'a pas été vérifiée : elle s'appuie sur des images apportées par le patch (`textbox/normal/NN.png` pour le
nom du personnage et la détection du mode histoire). Les autres options (sprites Original ou MangaGamer, BGM
d'origine, script expérimental) n'ont pas été essayées.

## Prérequis

- Windows, PowerShell, Git.
- **Android Studio** (fournit le SDK, adb et un JDK). Dans le SDK Manager, installer :
  - SDK Platform 35,
  - NDK **28.2.13676358**,
  - CMake **3.22.1**,
  - Platform-Tools.
- Variable `JAVA_HOME` vers le JDK d'Android Studio, par exemple
  `C:\Program Files\Android\Android Studio\jbr`.
- Un fichier `local.properties` à la racine du projet avec le chemin du SDK (Android Studio le crée à l'ouverture du
  projet ; sinon, à la main) :
  ```
  sdk.dir=C\:\\Users\\<toi>\\AppData\\Local\\Android\\Sdk
  ```
- Un téléphone **Android 10 ou plus**, **arm64**, avec le **débogage USB** activé, branché et autorisé
  (`adb devices` doit le lister), et **au moins 10 Go libres**.

## Installation

```powershell
git clone <url-du-depot> umineko-and
cd umineko-and
.\tools\deploy.ps1 -GameDir "C:\Program Files (x86)\Steam\steamapps\common\Umineko"
```

Le script `tools/deploy.ps1` fait, dans l'ordre :

1. **Build** : `gradlew assembleDebug` (la première fois, la compilation du moteur prend plusieurs minutes).
2. **Installation** de l'APK sur le téléphone (`adb install -r`).
3. **Création du dossier de données** : il lance l'appli une fois pour que le système crée
   `.../files/game` avec les bons droits (sinon `adb` en devient propriétaire et l'appli reçoit `EACCES`).
4. **Copie des données** : `adb push --sync` du dossier `-GameDir` (~9 Go, ~6 minutes en USB 3 ; les fichiers déjà
   à jour sont sautés aux exécutions suivantes).
5. **Lancement** de l'appli.

Options :

| Option | Effet |
|---|---|
| `-GameDir <dossier>` | Dossier Steam du jeu (obligatoire sauf avec `-SkipPush`) |
| `-SkipBuild` | Ne recompile pas, installe l'APK déjà construit |
| `-SkipInstall` | N'installe pas l'APK |
| `-SkipPush` | Ne copie pas les données (mise à jour du code seule) |
| `-Serial <numéro>` | Cible un appareil précis si plusieurs sont branchés (`adb devices`) |

Exemples :

```powershell
# Après une modification du code : build + installation, données déjà sur le téléphone
.\tools\deploy.ps1 -SkipPush

# Données seules (APK déjà installé)
.\tools\deploy.ps1 -SkipBuild -SkipInstall -GameDir "E:\SteamLibrary\steamapps\common\Umineko"
```

### Copie à la main

Si tu préfères ne pas utiliser le script : installer l'APK (`app\build\outputs\apk\debug\`), **lancer l'appli une
fois**, la fermer, puis :

```powershell
adb push "C:\...\Umineko\." /sdcard/Android/data/dev.umineko.android/files/game/
```

## Utilisation

- Toucher = avancer. Balayage vers le haut ou « Histo » = historique. Barre Auto / Skip / Menu.
- Retour Android = pause. Les sauvegardes se font depuis le menu du jeu.
- Portrait : la scène est rognée sur les côtés, le texte est affiché par Android. Paysage : le moteur dessine tout.

## Dépannage

| Symptôme | Cause probable |
|---|---|
| Écran noir au lancement, `logcat` (tag `Ponscr`) : script introuvable | Données absentes ou mal copiées dans `files/game/` |
| `EACCES` dans `logcat` | Le dossier `game/` a été créé par `adb` avant l'appli : le supprimer, lancer l'appli, recopier |
| « Movie not found » | Vidéos manquantes : le patch remplace `movie/umineko_op.mpg` ; recopier le dossier |
| `adb devices` vide | Débogage USB non autorisé, câble de charge seul, ou pilote USB |
| Le build échoue sur le NDK ou CMake | Versions différentes de celles listées dans les prérequis |

Journal de l'appli : `adb logcat -s Ponscr UminekoText`.

## Organisation du dépôt

```
app/          application Android (Java, CMake) : activité, interface native, pont JNI
engine/       ponscripter-fork (07th-Mod) avec les modifications Android sous #ifdef ANDROID_PORT
third_party/  SDL2, SDL2_image, SDL2_mixer, FreeType, libogg, libvorbis
tools/        deploy.ps1
docs/         recherche, décisions techniques, différences avec le moteur d'origine
```

Les choix techniques et le journal sont dans [`docs/02-decisions.md`](docs/02-decisions.md).

## Licence

Le moteur est sous GPLv2 (voir `engine/COPYING`). Le jeu et ses assets appartiennent à leurs ayants droit : ne les
publie pas, ne les ajoute pas au dépôt, et ne distribue pas d'APK qui les contiendrait.
