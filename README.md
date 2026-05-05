# MamY (working code-name)

App Android always-on qui transforme le debrief vocal post-1:1 en mémoire vivante par employé, pour managers de 30 à 100 personnes.

## Statut
**2026-05-04** — V1 alpha shipped. Wave 1 (P1-P9) merged on `main`. Wave 2
P8 signing pipeline live: signed APK + GitHub Releases workflow ready.

## Install (alpha sideload)

> **Pre-release alpha.** Sideload-only for the moment. No Play Store yet.

### What testers need

1. An **Android 9+ device** (API 28+, ARM64 strongly recommended).
2. A free **Picovoice AccessKey** from <https://console.picovoice.ai/>.
3. Trained `mamy_en.ppn` + `mamy_fr.ppn` files (Picovoice Console > Wake Word).
4. An **Anthropic Claude API key** (`sk-ant-...`) — bring-your-own-key, no MamY
   server in the loop. Get one at <https://console.anthropic.com/>.

### Step-by-step

1. Open **<https://github.com/sxc3030-eng/mamy/releases/latest>** on your
   Android device (URL placeholder — replace with the real repo).
2. Tap `app-release.apk` to download.
3. When Android prompts "For your security, your phone is not allowed to install
   unknown apps from this source": tap **Settings** > toggle **Allow from this
   source** > back > tap the APK again.
4. Tap **Install**, then **Open**.
5. Walk through the **7-step onboarding**: mic + notification permissions →
   wake-word setup → Picovoice key → Claude API key → done.
6. Drop your `mamy_en.ppn` + `mamy_fr.ppn` into `Documents/MamY/wakeword/` (use
   any file manager, or use the in-app prompt).
7. Test wake-word: say "MamY" → expect a haptic + chime within ~1 s.
8. Run the [smoke checklist](docs/SMOKE_CHECKLIST.md) to validate your install.

### Reporting issues

File issues at <https://github.com/sxc3030-eng/mamy/issues> with:
- Device model + Android version
- Failed scenario number from `docs/SMOKE_CHECKLIST.md`
- Logs from Settings > Logs > Share (no PII / no audio is logged)

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
