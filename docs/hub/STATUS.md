# MamY — Statut live (2026-05-03)

## Numbers cumulatifs

- **Commits sur main** : ~114 (22 P1 + 18 P2 + 23 P3 + 17 P4 + 17 P5 + 17 P6) + 3 WIP sur p7-ui
- **HEAD main** : `23ea1f5`
- **Tags** : 6 checkpoints `checkpoint/mamy-p<N>-complete-2026-05-03` pour P1→P6
- **Tests** : **281 pass · 8 @Ignore (KeyStore, attendent emulator) · 0 fail**
- **Native binaries** : `libmamy_whisper.so` produit pour 3 ABIs (arm64-v8a, armeabi-v7a, x86_64)
- **APK debug** : ~62 MB
- **GitHub** : https://github.com/sxc3030-eng/mamy (privé)

## Sub-plans status

| # | Sub-plan | Status | Tasks | Tests added |
|---|---|---|---|---|
| P1 | Foundation | ✅ SHIPPED | 21/21 | +38 |
| P2 | Voice Capture Pipeline | ✅ SHIPPED | 19/21 (T5 + T20-T21 instrumented deferred) | +21 |
| P3 | LLM Structurer | ✅ SHIPPED | 20/20 | +60 |
| P4 | Voice Intents & Memory | ✅ SHIPPED | 17/18 (T4 instrumented deferred) | +50 |
| P5 | Calendar Integration | ✅ SHIPPED | 17/18 (T18 instrumented deferred) | +30 |
| P6 | Briefing Engine | ✅ SHIPPED | 21/22 (T22 instrumented deferred) | +82 |
| **Sous-total P1-P6** | | **shipped** | **115/120** | **+281** |
| P7 | UI Compose | 🟡 WIP | 3/26 commits (BaseViewModel + Routes + deps) | – |
| P8 | Privacy Polish Beta | ⚪ pending | 0/26 | – |
| P9 | SMS Vocal Feature (NEW 2026-05-03) | 🟡 design en cours | 0/~18 estimé | – |

## Smoke test (2026-05-03 PM)

```bash
cd D:\ComfyUI-Intel\mamy
./gradlew :app:assembleDebug          # ✅ BUILD SUCCESSFUL (~58 sec)
./gradlew :app:testDebugUnitTest      # ✅ 281 tests, 0 fail
./gradlew :app:externalNativeBuildDebug  # ✅ .so produites
```

## Ce qui marche end-to-end (en code, en attente d'UI)

✅ Wake-word « MamY » détecté (Porcupine custom, sensibilité tunable)
✅ Audio captured 16 kHz mono, VAD silence cut 1.5s, max 90s
✅ Whisper STT local FR + EN (~75 MB modèle téléchargé first-run)
✅ 10 voice intents grammar (FR + EN) + LLM fallback extraction
✅ LLM cloud BYOK Claude / OpenAI / Gemini (stub V1.1)
✅ JSON structuration → 9 tables Room SQLCipher encrypted
✅ Person fuzzy matcher pour homonymes
✅ Undo last (≤30s) + Correct last + Person query
✅ Google Calendar OAuth + sync 15 min + person matching
✅ 4 types briefing : daily 8h + pre-meeting 5 min + person query + EOD
✅ WorkManager schedulers (DailyBriefingWorker + PreMeetingScheduler)
✅ TTS Android natif FR / EN, queue, interruptible
✅ Notifications channels + deep links vers PlayBriefingActivity
✅ Cost tracker per LLM provider per month
✅ TTS confirm post-capture (« Noté, 2 actions, 1 personne flaggée »)

## Ce qui ne marche pas encore (manque UI)

❌ Configurer ses BYOK keys (Picovoice, Claude/GPT) → besoin Settings UI (P7)
❌ Voir sa liste de reports → ReportsListScreen placeholder (P7)
❌ Voir l'historique d'une personne → PersonDetailScreen placeholder (P7)
❌ Voir les actions ouvertes → ActionsScreen placeholder (P7)
❌ Onboarding flow (permissions + wake-word test) → P7
❌ Connecter Google Calendar → bouton Settings (P7)
❌ Network log transparency → NetworkLogScreen (P7)
❌ Export tout / wipe → DataScreen (P7) + crypto (P8)
❌ Signed APK pour sideload partage → P8

## Setup user critique avant test live

1. **Picovoice Console** : créer compte + train custom phrase « MamY »
   - English → `mamy_en.ppn` → drop dans `app/src/main/assets/wakeword/`
   - French → `mamy_fr.ppn` → idem
2. **AccessKey Picovoice** : à sauver dans SecretsVault (P7 fera l'UI)
3. **Clé Claude/GPT/Gemini** BYOK (P7 fera l'UI)
4. **Internet** au premier launch : whisper-tiny ~75 MB se télécharge

## Branches GitHub

- `main` (HEAD `23ea1f5`)
- `p1-foundation` (preserved)
- `p2-voice-capture` (preserved)
- `p3-llm-structurer` (preserved)
- `p4-voice-intents-memory` (preserved)
- `p5-calendar` (preserved)
- `p6-briefing` (preserved)
- `p7-ui` (3 WIP commits)
