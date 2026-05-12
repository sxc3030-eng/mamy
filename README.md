# MamY (working code-name)

App Android always-on qui transforme le debrief vocal post-1:1 en mémoire vivante par employé, pour managers de 30 à 100 personnes.

## Statut

**2026-05-12** — V1.5 alpha live · APK `v0.4.5-alpha` distribuée via tunnel
Cloudflare. Zéro setup utilisateur (pas de clé API, pas de `.ppn` à entraîner,
pas de compte cloud à créer). Backend autonome géré côté serveur perso (i5
Debian + Cloudflare Tunnel + Groq Llama-3.1-8B).

## Install (alpha sideload)

> **Pre-release alpha.** Sideload only. Pas de Play Store.

### Ce que les testeurs doivent avoir

- **Un appareil Android 9+** (API 28+, ARM64 fortement recommandé).
- **C'est tout.** Pas de compte Picovoice, pas de clé Anthropic, pas de
  `.ppn` à dropper. La build alpha embarque tout ce qu'il faut.

### Étapes

1. Sur ton tel, ouvre la page d'install :
   <https://friends-muscle-warriors-formula.trycloudflare.com/>
2. Tape **« Télécharger l'APK »** — récupère `MamY-v0.4.5-alpha.apk` (≈ 59 MB).
3. Si Android dit « Sources inconnues bloquées » : ouvre **Settings**,
   active « Autoriser depuis cette source » pour ton navigateur, reviens
   tape l'APK à nouveau.
4. Tape **Install** puis **Ouvrir**.
5. Onboarding 6 étapes :
   1. Permissions (mic + notifs)
   2. Wake-word — tape « Use built-in JARVIS » (pas besoin de clé)
   3. SMS opt-in (skip si tu veux)
   4. Calendar — autorise `READ_CALENDAR` (lit ton agenda téléphone direct,
      pas d'OAuth Google)
   5. Test wake-word — dis « **Jarvis** » → chime
   6. Done
6. Lance la [smoke checklist](docs/SMOKE_CHECKLIST.md) pour valider ton
   install (~10 scenarios, 15 min).

### Wake-word « MamY » vs « Jarvis »

V1.5 alpha utilise **JARVIS built-in Picovoice** par défaut — zéro setup,
mais l'app ne répond pas à « MamY ». Pour récupérer le wake-word « MamY »,
les builds suivantes incluront `mamy_en.ppn` + `mamy_fr.ppn` entraînés sur
Picovoice Console (dossier `app/src/main/assets/wakeword/`). Quand les
`.ppn` sont présents, le résolveur les utilise automatiquement — sinon
fallback JARVIS. Pas de modif de code à faire pour switcher.

### Reporting issues

File issues at <https://github.com/sxc3030-eng/mamy/issues> with:

- Device model + Android version
- Failed scenario number from `docs/SMOKE_CHECKLIST.md`
- Logs from Settings > Logs > Share (no PII / no audio is logged)

## Pitch

Un manager de 30-100 personnes finit son 1:1 en salle de réu, marche dans
le corridor, parle 60-90 sec à son téléphone (« *Jarvis, prends note...* »),
et l'app structure tout par employé : état émotionnel, promesses faites des
deux côtés, actions à suivre. Avant chaque 1:1 suivant, briefing vocal de
30 sec sur ce qu'il faut savoir. Promesses qui dérapent → relance auto.

## Wedge (V1)

**Capture passive post-meeting** en debrief vocal libre. Pas d'enregistrement
live des réunions (pas de friction légale, pas d'intégration bot Zoom/Teams
en V1).

## Cible

Managers d'équipes 30-100 employés, marché premium nord-américain.

## Stack actuelle (v0.4.5-alpha)

- **Android natif** (Kotlin, Jetpack Compose), API 28+
- **Foreground service** `MamYListenerService` always-on (~5-8 % batterie/jour)
- **Wake-word** : Picovoice Porcupine — `JARVIS` built-in (v0.4.5) ou custom
  `mamy_<lang>.ppn` quand fourni (≈100 KB chacun)
- **STT** : Android `RecognizerIntent` (Gboard / Samsung Voice / etc.) pour
  les FAB Voice ; Whisper-tiny local (≈75 MB) pour la capture longue Reports
- **Structuration LLM** : `OllamaProvider` → Cloudflare Tunnel → backend i5
  Linux → Groq Cloud (`llama-3.1-8b-instant`) avec fallback Ollama local sur
  5xx. BYOK Claude/OpenAI/Gemini reste disponible dans Settings pour power
  users.
- **Storage local** : Room + SQLCipher (DB chiffrée, master key Android
  Keystore — fix v0.4.4 pour Android 12+ IV bug)
- **Calendar** : `CalendarContract` direct (Google, Outlook/Exchange,
  Samsung, iCloud-via-account — tout ce qui est synchro sur le tel). Pas
  d'OAuth, pas de tokens, pas de network.
- **Meeting reminders** : WorkManager — notifs 24h + 1h avant chaque réunion
  avec attendees connus.
- **TTS** : Android natif pour briefing vocal + read-aloud notes/actions.
- **Crash reporting** : `CrashReporter` early-install POST → backend
  `/api/crash` → log centralisé sur i5.

## UI v0.4.5

Bottom navigation 5 onglets : **Rapports** · **Actions** · **Agenda** ·
**Notes** · **Réglages**. Chaque écran de capture (Rapports, Notes, Actions)
expose un **FAB 🎤 voice-first** pour dicter directement sans ouvrir de
dialog texte.

## Naming

« Mamy » = nom du produit. Wake-word actuel = « Jarvis » (built-in
Picovoice) — sera « MamY » dès qu'on shippe les custom `.ppn`.

## Plateforme V1 → V2

- **V1 Android** (dev sur Windows, test direct sur tel)
- **V2 iOS** plus tard (Swift natif pour wake-word)

## Privacy stance (v0.4.5)

- **Audio brut jamais stocké** (default), opt-in seulement
- **Audio jamais envoyé cloud** — STT systeme (FAB Voice) tourne via le moteur
  Gboard du tel, Whisper-tiny tourne on-device.
- **Texte structuré** → DB locale chiffrée SQLCipher.
- **LLM cloud (Groq via tunnel) reçoit le TRANSCRIPT** (pas l'audio) pour
  structurer. C'est le compromis V1.5 alpha pour zéro-setup. Power users
  peuvent switcher vers BYOK Claude/OpenAI/Gemini dans Settings, ou mode
  Strict offline (capture sauve sans structurer cloud).
- **Crash reports** : trace + device model envoyés à `/api/crash`, no PII,
  no audio.

## Voir aussi

- [docs/SMOKE_CHECKLIST.md](docs/SMOKE_CHECKLIST.md) — checklist 20 scenarios
- [docs/brainstorm/2026-05-02-design-wip.md](docs/brainstorm/2026-05-02-design-wip.md)
  — brainstorm original (sections 1-2 validées, 3-7 historiques)
