# BG IPTV — CLAUDE.md

Référence projet pour toutes les sessions Claude Code.
Toutes les décisions ici ont été validées avec le propriétaire.

---

## Vision

App IPTV Android TV **event-first** — centrée sur les matchs et événements sportifs, pas sur les chaînes.
Le problème à résoudre : "j'ouvre l'app le soir pour voir un match, mais je perds 10min à trouver sur quelle chaîne c'est diffusé."

**Différenciateurs vs concurrents (Tivimate, Smarters, IPTV Pro) :**
- Event-first home : matchs surfacés sans chercher, avec chaînes où c'est diffusé
- Browser style Norton Commander (2 colonnes, dense, D-pad, overlay transparent pendant lecture)
- Déduplication intelligente des chaînes (TF1 / TF1 HD / TF1 4K → une seule entrée)
- Setup via QR code + page web sur téléphone (zéro saisie avec la télécommande)
- Buffer adaptatif par contexte (sport / film / zapping)
- Zéro télémétrie, zéro cloud, credentials chiffrés hardware

---

## Stack technique

| Composant | Choix |
|---|---|
| Langage | Kotlin |
| UI | Jetpack Compose for TV |
| Lecteur vidéo | Media3 (ExoPlayer) |
| BDD locale | Room + SQLCipher (chiffrement) |
| DI | Hilt |
| Réseau | Retrofit + OkHttp |
| Sécurité credentials | EncryptedSharedPreferences + Android Keystore |
| Concurrence | Coroutines + Flow |
| Images | Coil |
| Tests | JUnit5 + MockK + Turbine |

---

## Cible hardware

- **TV principale** : Freebox Pop (Android TV 10, Amlogic SoC, 2GB RAM, 8GB storage)
- **Écran** : Samsung QLED 49" 2019 (Q60R/Q70R/Q80R range)
  - HDR10 + HDR10+ ✅
  - Dolby Vision ❌ (jamais sur Samsung)
  - Panel 60Hz natif
  - HDMI 2.0 (4K60 max)

**Défauts vidéo calibrés pour ce setup :**
```
HDR              : Auto (HDR10 OK, DV désactivé)
Dolby Vision     : OFF (hardcodé)
Codec            : HEVC HW (Auto)
Frame rate match : ON (50Hz/60Hz switching via HDMI)
Tone mapping     : Auto avec fallback SDR
Buffer profil    : Zapping 15s (auto-switch selon contexte)
Audio            : Auto passthrough HDMI
Décodage         : Hardware + Tunneling ON
Cache disque LRU : 500MB
```

---

## Source de données

**Provider IPTV** : Xtream Codes (URL serveur + username + password)
- API live channels : `get_live_categories` + `get_live_streams`
- API EPG court : `get_short_epg`
- API EPG long : `get_simple_data_table`
- API VOD : `get_vod_categories` + `get_vod_streams` + `get_vod_info`
- API séries : `get_series_categories` + `get_series` + `get_series_info`
- Stream URL live : `http://host:port/username/password/stream_id`
- Catch-up (si supporté) : `http://host:port/timeshift/user/pass/duration/start/id`

**Sources externes (enrichissement sport) :**
- `football-data.org` (free tier) : Ligue 1, Champions League, Premier League, La Liga
- `balldontlie.io` (free) : NBA
- `TheSportsDB` (free) : calendriers multisports
- `TMDB API` (V2, clé fournie par l'utilisateur) : métadonnées VOD

---

## Architecture modules

```
app/
├── core/
│   ├── data/           # Room DB, DAOs, entités
│   ├── network/        # Retrofit, Xtream API client, sport APIs
│   ├── security/       # Keystore wrapper, EncryptedPrefs, setup QR
│   └── player/         # Media3 wrapper, buffer profiles, cache
├── feature/
│   ├── setup/          # Onboarding, QR pairing, import playlist
│   ├── browser/        # Norton browser (composant central)
│   ├── player/         # Lecteur fullscreen + overlays
│   ├── sport/          # Vue sport aujourd'hui, détail event, radar (V3)
│   ├── search/         # Recherche unifiée
│   ├── vod/            # Films + séries VOD (V2)
│   └── settings/       # Réglages 3 niveaux
└── ui/
    ├── theme/          # Design tokens, couleurs, typo
    └── components/     # Composants partagés (cards, overlays, focus)
```

---

## UI — Browser Norton Commander (écran central)

Interface à 2 colonnes, semi-transparente en overlay pendant la lecture, plein écran au boot.

**Colonne gauche — Groupes (ordre fixe) :**
1. 🔴 EN DIRECT (events live, count affiché)
2. ⏰ BIENTÔT (dans les 2h)
3. ⏯ REPRENDRE (dernière chaîne)
4. Séparateur
5. Sports (Foot, F1, NBA, Tennis, Combat...)
6. Séparateur
7. Pays (🇫🇷 France, 🇬🇧 UK, 🇪🇸 Espagne, 🇮🇹 Italie...)
8. Séparateur
9. Thématiques (Info, Cinéma, Jeunesse, Docs, Musique)
10. Séparateur
11. ⭐ Favoris
12. 🎬 Films VOD (V2)
13. 📺 Séries VOD (V2)

**Colonne droite — Contenu du groupe sélectionné :**
- Mode "chaînes" : liste chaînes + EPG inline (programme actuel abrégé)
- Mode "events" (quand groupe EN DIRECT / BIENTÔT) : cartes événements avec score, minute, compétition, chaînes où ça passe
- Mode "VOD" : grille avec posters TMDB

**Comportement overlay :**
- Au boot : plein écran sur fond noir (ou dernière chaîne en fond transparent)
- Pendant lecture : semi-transparent 70% + blur léger, vidéo perceptible derrière
- Touche Back : ferme overlay, lecture reste
- Disparaît auto si pas d'action pendant 8s (configurable)

**Touches télécommande Freebox :**
- ↑↓ : navigation dans colonne courante
- ←→ : switch entre colonnes
- OK : sélectionner / ouvrir
- Long OK : toggle favori
- Back : fermer overlay
- 🟡 Jaune : recherche
- 🔵 Bleu : radar (V3)
- 🟢 Vert : score overlay (V2)
- 🔴 Rouge : menu contextuel
- Chiffres 0-9 : zap LCN direct

---

## Sécurité — règles non négociables

1. **Credentials** stockés via `EncryptedSharedPreferences` + Android Keystore (TEE/StrongBox)
2. **android:allowBackup="false"** dans le manifest — jamais de backup Google Drive
3. **Aucun SDK tiers** de tracking : pas de Firebase, pas de Crashlytics, pas d'Analytics
4. **Transmission QR setup** : clé éphémère dans le QR, credentials chiffrés avant envoi en local
5. **Connexions sortantes** : provider IPTV + APIs sport/TMDB + endpoint update seulement. Affichées dans Réglages > Sécurité.
6. **PIN optionnel** (désactivé par défaut) pour protéger l'accès à l'app

---

## Déduplication des chaînes

Algorithme de normalisation des noms pour fusionner les doublons :
1. Strip préfixes pays : `FR|`, `[FR]`, `🇫🇷`, `FR -`, `FR:`
2. Strip qualité : `HD`, `FHD`, `UHD`, `4K`, `8K`, `SD`, `HQ`, `LQ`, `H265`, `HEVC`, `AVC`
3. Strip encodage spéciaux : emojis, caractères unicode décoratifs
4. Strip espaces multiples, tirets en fin/début
5. Lowercase + trim
6. Fuzzy match (Levenshtein ≤ 2) pour attraper les typos du provider

Résultat : une `ChannelGroup` par chaîne logique, avec N `ChannelVariant` (SD/HD/4K).
L'app sélectionne automatiquement la meilleure variante selon la bande passante détectée.
L'user peut forcer une variante par chaîne (mémorisé en DB).

---

## Buffer profiles

```kotlin
enum class BufferProfile {
    SPORT,   // minBuffer=8s, maxBuffer=30s, playbackStart=2s, rebuffer=4s
    FILM,    // minBuffer=30s, maxBuffer=120s, playbackStart=5s, rebuffer=15s
    ZAPPING, // minBuffer=15s, maxBuffer=50s, playbackStart=2.5s, rebuffer=5s
}
```

**Sélection automatique :**
- Chaîne EPG-taggée sport + event live en cours → SPORT
- VOD film/série → FILM
- Défaut → ZAPPING

**Cache disque :** `SimpleCache` LRU 500MB pour segments HLS récents.
**Pré-buffer :** chaîne précédente + suivante dans le groupe courant (désactivable en settings).

---

## Enrichissement sport V1

**Football (priorité absolue) :**
- Source : `football-data.org` free tier
- Compétitions couvertes V1 : Ligue 1, Champions League, Europa League, Premier League, La Liga
- Données : calendrier matchs, scores live (polling 60s pendant les matchs), buteurs, classements
- Cross-référence avec EPG Xtream pour trouver les chaînes qui diffusent

**Logique de matching event → chaîne :**
```
1. Récupérer tous les events du jour depuis l'API sport
2. Pour chaque event, générer des patterns (ex: "PSG", "Paris Saint-Germain", "Champions League")
3. Scanner l'EPG de toutes les chaînes "sport" de la playlist
4. Matcher par regex/fuzzy sur les titres de programmes
5. Associer event ↔ chaîne(s) avec un score de confiance
6. Afficher les matchs avec > 0.8 confiance dans EN DIRECT / BIENTÔT
```

**Base de connaissances broadcasters FR (hardcodée, mise à jour via JSON versionné) :**
```json
{
  "ligue_1": ["Canal+ Foot", "DAZN", "Ligue1+"],
  "champions_league": ["Canal+", "beIN Sports"],
  "premier_league": ["Canal+"],
  "la_liga": ["beIN Sports"],
  "nba": ["beIN Sports"]
}
```

---

## Distribution

- **Format** : APK signé
- **Hébergement** : URL courte (à définir) + GitHub Releases
- **Auto-update** : l'app check `https://[host]/version.json` au lancement
  - Si nouvelle version → notif discrète "Mise à jour dispo v1.x.x → Installer"
  - Download APK + prompt install via `PackageInstaller`
  - Aucune donnée envoyée lors du check (GET simple)

---

## Setup premier lancement

1. **Écran welcome** : choix "Avec mon téléphone" (recommandé) ou "Télécommande"
2. **QR setup** :
   - TV génère clé éphémère 256-bit
   - QR encode `{url: "http://[tv-local-ip]:8080", key: "[ephemeral-key]"}`
   - Websocket serveur local sur la TV
   - Page web sur le téléphone : formulaire Xtream (URL + user + pass) ou paste lien complet
   - "Coller depuis lien complet" parse automatiquement `get.php?username=X&password=Y`
   - Credentials chiffrés avec la clé éphémère avant envoi
   - TV reçoit, déchiffre, stocke dans EncryptedSharedPreferences
   - Clé éphémère détruite immédiatement
3. **Import** : parsing Xtream en coroutines IO, lecture disponible en 5s, reste en arrière-plan
4. **Déduplication** : lancée en arrière-plan pendant l'import
5. **Première chaîne** : auto-play dès que la playlist est prête (pas d'attente)

---

## Roadmap

### V1 — MVP différenciant
- [ ] Squelette projet (modules, gradle, manifest)
- [ ] Sécurité (Keystore, EncryptedPrefs)
- [ ] Setup QR + web phone + import Xtream
- [ ] Parser Xtream (live + EPG)
- [ ] Déduplication chaînes
- [ ] Room DB (channels, programs, events, history, favorites)
- [ ] Browser Norton 2 colonnes (Compose for TV)
- [ ] Lecteur Media3 + 3 profils buffer
- [ ] Pré-buffer voisins + cache disque
- [ ] Enrichissement foot football-data.org
- [ ] Matching events ↔ chaînes
- [ ] Home EN DIRECT / BIENTÔT / REPRENDRE
- [ ] Recherche unifiée (channels + events + EPG)
- [ ] Favoris par groupe
- [ ] Historique silencieux
- [ ] Settings 3 niveaux (BASE / AVANCÉ / EXPERT)
- [ ] Réglages par chaîne (override codec/qualité)
- [ ] PIN optionnel
- [ ] Onglet "Connexions sortantes" dans sécurité
- [ ] Auto-update APK

### V2 — Différenciation poussée
- [ ] VOD films + séries Xtream + enrichissement TMDB
- [ ] Score live cross-canal overlay
- [ ] Alerte but automatique
- [ ] NBA + F1 enrichissement
- [ ] Multi-provider failover
- [ ] Catch-up TV (si provider supporte)
- [ ] Phone-as-remote permanent (web app)

### V3 — Wow effect
- [ ] Match Radar (grille multi-matchs live)
- [ ] Drama auto-switch
- [ ] Multi-pin / split screen
- [ ] Hover audio preview dans browser
- [ ] Google Assistant voice control
- [ ] Storylines / contexte événements

---

## Conventions de code

- **Langue du code** : anglais (variables, fonctions, commentaires)
- **Langue UI** : français (V1 seulement, archi i18n-ready avec string resources)
- **Architecture** : Clean Architecture, MVVM + UiState
- **Naming** : `XxxViewModel`, `XxxUseCase`, `XxxRepository`, `XxxDao`
- **Pas de commentaires évidents** — seulement si le WHY n'est pas clair
- **Coroutines** : `viewModelScope` pour UI, `Dispatchers.IO` pour réseau/DB
- **Erreurs** : `Result<T>` ou `sealed class` pour les états, pas d'exceptions non catchées
- **Pas de mock DB en tests** — utiliser Room en mémoire (`Room.inMemoryDatabaseBuilder`)

---

## Licence

Open source avec licence custom (style BUSL) :
- Lecture du code autorisée
- Contributions via PR bienvenues
- Fork interdit sans accord
- Usage commercial interdit sans accord
- Voir `LICENSE` à la racine
