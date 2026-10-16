# Design

## Choix du skill design-ui
- **Date / mode**: 2026-10-16, init (appelé par project-kickoff, projet existant).
- **Direction**: sur-mesure « Livre-objet discret » — prolonge la palette déjà en place (nuit violette, crème, or), la
  rationalise en tokens. Aucun design system formel n'existait (couleurs écrites en dur dans `MainActivity.java`).
- **Méthode**: `methodes/impeccable.md` (application/outil : efficacité d'usage d'abord).
- **Utilisateurs**: le développeur lui-même et quelques proches/contributeurs, lecteurs d'un long visual novel,
  sessions longues, téléphone en portrait, souvent à une main.
- **Plateformes**: Android (minSdk 29), téléphone portrait prioritaire ; paysage = le moteur dessine tout.
- **3 tâches principales** (fréquence): 1) lire et avancer le texte (en continu) ; 2) Auto / Skip / Historique /
  sauvegarde rapide (plusieurs fois par session) ; 3) choisir, sauvegarder/charger, régler les options (occasionnel).
- **Ton en 3 mots**: discret, littéraire, sombre.
- **Anti-objectifs**: pas de look Material / app générique ; pas de texte < 16 sp ni de cible < 48 dp ; pas
  de contrôle caché (voir règle ci-dessous). Ornements lourds/kitsch non retenus par l'utilisateur (pas coché, à
  éviter par prudence).
- **Priorité immersion vs info**: immersion — scène la plus grande possible, contrôles estompés au repos mais
  atteignables au pouce (bas de l'écran).
- **Règle des contrôles**: toute action fréquente (Auto, Skip, Historique, sauvegarde rapide, Menu) a un bouton
  visible. Les gestes (balayage haut = historique, tap = avancer) sont des raccourcis, jamais le seul accès.
- **Thèmes**: couleurs/polices/formes centralisés en tokens interchangeables (package `ui/theme`) ; 1 thème livré
  (« Livre-objet »), 1 second thème possible plus tard (ex. neutre « app de lecture »). Une seule disposition,
  optimisée portrait. Dispositions alternatives hors périmètre tant que l'UI n'est pas migrée en Compose.
- **Contraintes**: libellés en anglais (jeu EN/JP, texte japonais possible : prévoir polices CJK système, pas
  d'espacement de mots) ; thème sombre uniquement ; taille de texte réglable A−/A+ (17–26 sp par défaut selon la
  largeur) et respect de la taille de police système ; zones sûres (encoche, barre de navigation).

### Tokens de départ (thème « Livre-objet »)
Valeurs reprises du code actuel, contrastes calculés approximativement (à re-vérifier à la capture).
| Rôle | Valeur | Texte posé dessus | Contraste |
|---|---|---|---|
| Fond panneau de lecture | `#100E14` | texte `#F0ECE4` | ~16:1 |
| Fond surface (cartes, boutons) | `#342E46` | texte `#FFFFFF` / secondaire `#BEB8CE` | ~11:1 / ~6,6:1 |
| Surface atténuée (emplacement vide) | `#24222F` | texte `#78728A` (vide) | ~2,7:1 — **sous AA, à corriger** |
| Accent action principale | `#843C58` | texte `#FFFFFF` | ~8:1 |
| Danger (retour au titre) | `#682A32` | texte `#FFFFFF` | ~10:1 |
| Nom du personnage / badge | `#E8BE78` | sur `#100E14` | ~11:1 |
| Texte de lecture | `#F0ECE4` (crème) | sur `#100E14` | ~16:1 |

- **Typographie**: serif système pour le texte du jeu et les titres (livre) ; sans-serif système pour les libellés de
  commande. Texte de lecture 17–26 sp (réglable), libellés de boutons >= 16 sp, jamais en dessous.
- **Espacements**: grille de 4 dp ; marges d'écran 16 dp ; espacement entre cibles >= 8 dp ; cibles >= 48 dp.
- **Rayons**: 12 dp cartes/boutons, 999 dp pastilles. **Ombres**: aucune (séparation par teinte de surface).
- **Mouvement**: 150–250 ms, ease-out, fondu uniquement ; réduit si les animations système sont désactivées.

### Risque esthétique assumé
Le panneau de texte en bas occupe la moitié de l'écran en portrait : l'écart entre le texte et les visages des
personnages (signalé comme gênant en portrait dans les retours de développeurs) est accepté au profit du confort de
lecture. Garde-fou: hauteur du panneau réglable et scène recadrée pour garder les personnages visibles (déjà fait).

## Directions rejetées
- *Fidèle à l'UI d'origine* (cadres, ornements PC/PS3): risque de pastiche et d'assets sous droits.
- *Moderne neutre « app de lecture »*: pas retenu par défaut ; possible comme second thème.
- *Dispositions alternatives complètes*: coût élevé avant migration Compose.

## Preuves et références (recherche du 2026-10-16, faible densité)
- Cibles tactiles >= 48x48 dp espacées de >= 8 dp — Google Accessibility Help,
  https://support.google.com/accessibility/android/answer/7101858 (source officielle).
- Tiers bas de l'écran = zone du pouce ; coins hauts difficiles — guides secondaires (UXPin, OpenReplay…) ; le chiffre
  « 49 % en une main » (Hoober) **n'a pas pu être vérifié**.
- Visual novels en portrait: adapter largeur et marge basse de la boîte de dialogue aux écrans 9:20, composer les fonds
  au centre, garder des positions de boutons conventionnelles, UI discrète — devlogs itch.io
  (https://itch.io/blog/895918/visual-novel-ui-and-accessibility,
  https://redseedgames.itch.io/dusk-point-reloaded-mikos-journey/devlog/884815/an-attempt-on-portrait-visual-novels-for-android)
  et analyse « UI design – an anatomy of visual novels » (forums.fuwanovel.moe). Retours d'expérience, pas des études.
- Recherche japonaise (ノベルゲーム スマホ 縦画面 UI): rien de spécifique trouvé. Coréen/chinois non cherchés.

## Journal des changements de direction
- 2026-10-16 — création: direction « Livre-objet discret », thèmes par tokens, règle « pas de contrôle caché ».
