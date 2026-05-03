# Mamy (working code-name)

App Android always-on qui transforme le debrief vocal post-1:1 en mémoire vivante par employé, pour managers de 30 à 100 personnes.

## Statut
**2026-05-02** — Brainstorm / design phase. Voir [docs/brainstorm/](docs/brainstorm/).

## Pitch
Un manager de 30-100 personnes finit son 1:1 en salle de réu, marche dans le corridor, parle 60-90 sec à son téléphone (« *Mamy, prends note...* »), et l'app structure tout par employé : état émotionnel, promesses faites des deux côtés, actions à suivre. Avant chaque 1:1 suivant, briefing vocal de 30 sec sur ce qu'il faut savoir. Promesses qui dérapent → relance auto.

## Wedge (V1)
**Capture passive post-meeting** en debrief vocal libre. Pas d'enregistrement live des réunions (pas de friction légale, pas d'intégration bot Zoom/Teams en V1).

## Cible
Managers d'équipes 30-100 employés, marché premium nord-américain.

## Stack envisagé
- Android natif (Kotlin, Jetpack Compose), API 28+
- Foreground service "MamyListener" always-on (~5-8 % batterie/jour)
- Wake-word custom "Mamy" via Picovoice Porcupine (~100 KB on-device)
- STT local : whisper.cpp Android (whisper-tiny, ~75 MB)
- Structuration LLM cloud BYOK (Claude / GPT / Gemini), full-local optionnel (Phi-3 mini)
- Storage local : Room + SQLCipher (DB chiffrée, master key Android Keystore)
- Calendar : Google Calendar API + Microsoft Graph (OAuth)
- TTS Android natif pour briefing vocal

## Naming
« Mamy » = wake-word custom. Le nom commercial du produit reste à décider (voir section 7 du design).

## Plateforme V1 → V2
- **V1 Android** (dev sur Windows, test direct sur tel)
- **V2 iOS** plus tard (Swift natif pour wake-word)

## Privacy stance
- Audio brut **jamais stocké** (default), opt-in seulement
- Audio **jamais envoyé cloud** (Whisper run on-device)
- Texte structuré → DB locale chiffrée
- Cloud LLM = BYOK, user choisit son provider et fournit sa clé

## Voir aussi
- [docs/brainstorm/2026-05-02-design-wip.md](docs/brainstorm/2026-05-02-design-wip.md) — brainstorm en cours (sections 1-2 validées, 3-7 à venir)
