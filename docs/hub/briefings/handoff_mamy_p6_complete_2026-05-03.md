---
name: Mamy P6 Briefing Engine COMPLETE 2026-05-03
description: P1-P6 livré — backend complet, ~114 commits sur main, build green, 281 tests pass. P7 UI WIP partiel sur branche, P8 pas commencé.
type: handoff
originSessionId: 68c203be-d6a4-40c4-a377-8b1fdf6049d8
---
**LIRE EN PREMIER prochaine session Mamy** (état au 2026-05-03 PM, marathon P1-P6 même jour).

## État live (snapshot)
- **Repo** : `D:\ComfyUI-Intel\mamy\` · **GitHub** : https://github.com/sxc3030-eng/mamy
- **Branch `main`** : HEAD `23ea1f5`, 6 sub-plans mergés via `--no-ff`
- **Branches conservées sur GitHub** : `p1-foundation`, `p2-voice-capture`, `p3-llm-structurer`, `p4-voice-intents-memory`, `p5-calendar`, `p6-briefing`, `p7-ui` (WIP)
- **Tags** : `checkpoint/mamy-p1-complete-2026-05-03` jusqu'à `p6` — chacun un milestone vert
- **Total commits** : ~114 sur main + 3 sur p7-ui (WIP : BaseViewModel + accompanist deps + Routes refactor)

## Smoke test live
| Command | Résultat |
|---|---|
| `./gradlew :app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` | ✅ **281 tests / 0 failures / 0 errors / 8 skipped** (KeyStore tests @Ignore en attendant emulator) |
| `./gradlew :app:externalNativeBuildDebug` | ✅ libmamy_whisper.so produite arm64-v8a + armeabi-v7a + x86_64 |

## Ce qui est SHIPPED en code (P1 → P6)

### P1 Foundation (22 commits)
- Gradle 8.10.2 + Kotlin 2.0.21 + Compose BOM 2024.12.01 + Hilt 2.52
- Min SDK 28, Target SDK 35, JVM 17, package `com.mamy.android`
- AndroidManifest 7 permissions (mic + FGS + notifs + internet + wakelock + network)
- MamYApplication @HiltAndroidApp + Configuration.Provider (HiltWorkerFactory)
- Material 3 theme + dynamic color + big-text typography
- 6 placeholder routes (Onboarding, ReportsList, PersonDetail, Actions, Settings, NetworkLog)
- i18n FR + EN
- KeystoreHelper + SecretsVault (AES-256 GCM hardware-backed master key, BYOK keys + DB passphrase chiffrés)
- 8-table Room schema + SQLCipher (Person, Note, Action, Promise, Flag, Meeting, MeetingAttendee, Briefing)
- + LlmCostEntry ajoutée en P3 → 9 tables now, version=2 avec destructive migration
- TypeConverters Instant↔Long, UUID↔String
- SettingsRepository DataStore (langue, briefing time, LLM provider, privacy mode, wake-word sensitivity, locale, TTS rate)
- 3 Hilt DI modules (Database, Secrets, Settings)
- MamYListenerService skeleton

### P2 Voice Capture Pipeline (18 commits)
- Picovoice Porcupine wake-word custom (« MamY » FR + EN, sensibilité user-tunable, charge depuis `app/src/main/assets/wakeword/mamy_*.ppn`)
- AudioCapture (AudioRecord 16 kHz mono PCM, AtomicBoolean lifecycle, callbackFlow)
- VadProcessor + SimpleEnergyVad inline RMS-based (pas de lib externe — JitPack webrtc-vad était unreachable)
- whisper.cpp v1.7.2 vendoré sous `app/src/main/cpp/whisper-cpp/` (~5 MB pruné)
- NDK 26.3.11579264 + CMake 3.22.1 build natif 3 ABIs avec NEON FP16 flags
- JNI bridge init/transcribe/free
- WhisperEngineImpl Kotlin API + first-run model downloader (whisper-tiny ~75 MB sha256 vérifié)
- IntentRouter stub (P4 le remplace)
- CapturePipeline orchestrateur : Flow<CaptureEvent>
- MamYListenerService wired : wake-word continu → on detection pause → run capture → resume
- Volume-up long-press fallback bypass wake-word
- Permissions runtime RECORD_AUDIO + POST_NOTIFICATIONS
- 3 Hilt modules (WakeWord, Audio, Stt)

### P3 LLM Structurer (23 commits)
- BYOK provider abstraction (sealed `LlmProvider`)
- ClaudeProvider (HTTP /v1/messages)
- OpenAIProvider (HTTP /v1/chat/completions JSON mode)
- GeminiProvider stub V1.1
- ProviderFactory choisit selon Settings + injecte clé via SecretsVault
- StructuredNote data classes + JSON schema strict (kotlinx.serialization)
- StructuredNoteParser avec markdown-fence stripping + fallback non-structured
- PromptBuilder FR + EN (texte exact du spec)
- LlmCostTracker : Room entity LlmCostEntry + DAO + repository + Flow<MonthlyCost>
- CostCalculator (microcents precision, $/M-token rates par provider)
- LlmStructurer orchestrator (Success / RawFallback / Failure outcomes)
- NoteWriter persiste outcome aux DAOs (cascade Person+Note+Action+Promise+Flag)
- TtsConfirmer (Android TextToSpeech wrapper, FR+EN, singular/plural)
- StructuredCapturePipeline wire : StructuredCapturePipeline.handle(text, lang, durationSec) appelée depuis MamYListenerService.observeEvents() après TranscriptReady
- Settings UI hook (LlmSettingsViewModel + screen pieces, à finir en P7)
- Cost tracker UI hook (CostViewModel)
- Hilt LlmModule

### P4 Voice Intents & Memory (17 commits)
- `Intent` sealed class avec rawText (Capture / DailyBrief / NextBrief / PersonBrief / PromisesOwedMe / ActionsOpen / EodSummary / UndoLast / CorrectLast / Unknown)
- `IntentGrammar` regex compiled FR+EN (10 patterns + fallback)
- `IntentRouter` golden tests + remplacé le P2 stub
- `IntentDispatcher` exhaustive when, dispatched depuis StructuredCapturePipeline
- DAOs étendus avec memory query methods (findActiveOwedToSelf, findOpen, findByName, findActiveBetween, etc.)
- 4 briefing handler interfaces stubbés (P6 les remplace)
- `PromisesOwedMeHandler`, `ActionsOpenHandler` (DB query + format vocal + TTS)
- `LastNoteTracker` 30s ring-buffer
- `UndoLastHandler` cascade delete dans la fenêtre 30s
- `CorrectLastHandler` re-soumet à LlmStructurer + replace last note
- `PersonMatcher` (domain/memory/) fuzzy matcher pour homonymes
- `TemplatedPersonBriefHandler` V1 (P6 remplace par LLM)
- `CaptureHandler` (wraps P3 LlmStructurer + records into LastNoteTracker)
- `HomonymeClarifier` TTS round-trip
- IntentModule Hilt
- TextToSpeechAdapter pour P5/P6

### P5 Calendar Integration (17 commits)
- `data/calendar/google/` : CalendarTokens + CalendarTokenStore (EncryptedSharedPreferences) + CalendarAuthManager + CalendarApiModels + CalendarApiClient + CalendarHttpLogger
- `data/calendar/` : PersonMatcher (email-based, distinct du P4 fuzzy matcher) + InitialCalendarSyncUseCase + DeltaCalendarSyncUseCase + CalendarSyncWorker + CalendarSyncScheduler
- `data/settings/` : CalendarSettings flag + CalendarSyncStateStore
- `data/network/` : NetworkLogEntry + NetworkLogStore (transparence pro)
- `domain/memory/ConfirmPersonStubUseCase`
- `domain/calendar/CalendarOnboardingUseCase`
- `ui/screens/settings/CalendarSettingsViewModel`
- CalendarModule Hilt
- `res/values/calendar_config.xml` placeholder
- `docs/setup/oauth-google-cloud.md` runbook OAuth
- DAOs étendus (Person : findById/findByCalendarEmail/observeUnmatched ; Meeting+MeetingAttendee : upsert/findByCalendarEventId/etc.)
- MamYApplication wired : observe `calendarSettings.isCalendarEnabled` → schedule/cancel sync periodic
- 13 unit tests

### P6 Briefing Engine (17 commits)
- `domain/briefing/` : BriefingType enum, BriefingRequest, BriefingResult, BriefingPromptBuilder (4 templates × FR/EN, exact spec text)
- ContextAssembler (Room queries → JSON pour le prompt)
- BriefingCache (TTL daily=8h, pre-meeting=1h, person=0, eod=0)
- BriefingGenerator orchestrator (LlmProvider.complete → cache → return)
- DailyBriefHandler / PreMeetingBriefHandler / PersonQueryBriefHandler / EodSummaryHandler (remplacent P4 stubs)
- TtsService (queue, interruptible, distinct du P3 TtsConfirmer pour confirmations courtes)
- DailyBriefingWorker (WorkManager hourly self-gating, configurable time)
- PreMeetingScheduler (1-min check pour events upcoming)
- BriefingNotifier + 2 notification channels (daily + pre-meeting)
- PlayBriefingActivity + deep link `mamy://play/{briefingId}` (manifest registered)
- LlmProvider.complete() free-form completion ajoutée (vs structure-only) — ClaudeProvider impl le fait via /v1/messages
- IntentResult.Ok(spokenText) factory pour l'idiome plan-style
- BriefingModule Hilt + bridges au LlmProviderFactory + SettingsRepository
- 11 nouveaux tests, 281 total green

## Pas encore (P7 + P8 restent)

### P7 UI Compose (~26 tasks, branche `p7-ui` à 3 commits WIP)
- ❌ OnboardingScreen multi-step (permissions + calendar OAuth + BYOK + wake-word test)
- ❌ ReportsListScreen avec emotional trend + flags badge
- ❌ PersonDetailScreen avec sections (notes, promises, actions, flags)
- ❌ ActionsScreen avec swipe-to-done
- ❌ SettingsScreen modulaire (Account, BYOK, Briefings, Privacy, Cost, Language, Wake-word, About)
- ❌ NetworkLogScreen
- ❌ DataScreen (export tout / wipe)
- ❌ VoiceIndicator overlay
- ❌ Theme adjustments accessibility
- WIP commit `9e85bce` : Routes refactor sealed interface + path() helpers
- WIP commit `7f00d1b` : BaseViewModel asStateFlow helper
- WIP commit `392efdf` : accompanist-permissions + compose-ui-test deps

### P8 Privacy Polish Beta (~26 tasks, pas commencé)
- ❌ Export tout (JSONL gzipped + AES-PBKDF2)
- ❌ Wipe per person + wipe all
- ❌ CrashLogger local-only
- ❌ Battery instrumentation (Trace markers)
- ❌ Release Gradle config (signingConfig + R8 + ProGuard rules)
- ❌ App icon design final 512×512
- ❌ Play Console internal/open beta tracks
- ❌ Privacy policy + ToS markdown + GitHub Pages hosting
- ❌ Final smoke checklist
- ❌ Post-launch monitoring template

## Setup user TODO critique avant test live (sur device)

### Sans ces étapes, le wake-word et le LLM crash
1. **Picovoice Console** : créer compte + train custom phrase « MamY »
   - English → download → renommer `mamy_en.ppn`
   - French → download → renommer `mamy_fr.ppn`
   - Drop dans `D:\ComfyUI-Intel\mamy\app\src\main\assets\wakeword\`
   - AccessKey à sauver dans SecretsVault (P7 fera l'UI ; pour now hardcode debug)
2. **BYOK API key Claude/OpenAI** : sauver dans SecretsVault (P7 fera l'UI ; pour now hardcode debug)
3. **Internet first-run** : whisper-tiny ~75 MB se télécharge auto

### Optionnel
- `setx ANDROID_HOME "C:\Users\sxc_2\AppData\Local\Android\Sdk"` permanent
- Android Studio si besoin émulateur

## Pour reprendre P7 + P8

```bash
cd D:/ComfyUI-Intel/mamy
git checkout p7-ui   # WIP déjà là
# OU repartir clean :
# git checkout main && git checkout -b p7-ui-fresh
```

Puis ouvrir Claude Code dans `D:\ComfyUI-Intel\mamy\` (mnemo voit le projet séparé) et lancer :
- `superpowers:subagent-driven-development` sur `2026-05-02-mamy-p7-ui.md` (26 tasks)
- Puis pareil sur `2026-05-02-mamy-p8-privacy-polish.md` (26 tasks)

Pattern qui a marché : dispatch un agent par sous-plan, agent écrit tout le code séquentiellement, je merge + tag entre chaque.

## Capacités MamY actuelles (résumé pour user)

**Backend complet** : wake-word → audio → STT → LLM → DB → TTS + 10 voice intents + memory queries + calendar sync + 4 briefings + WorkManager schedulers.

**Bloque l'usage live** : pas d'UI pour entrer les API keys, voir les data, faire l'onboarding. P7 débloque ça. P8 = ship.

## Statistiques projet (cumulatif après P6)
- **~114 commits** sur main (22 P1 + 18 P2 + 23 P3 + 17 P4 + 17 P5 + 17 P6)
- **~150 fichiers source Kotlin/C++/XML/CMake**
- **281 tests pass + 8 @Ignore** = 289 total
- **APK debug** ~62 MB (avec native .so 3 ABIs + whisper.cpp)
- **6 tags checkpoints** sur GitHub
- **7 branches** sur GitHub (p1 → p7-ui)

## Plans restants
- **P7 UI Compose** (~26 tasks, 3 WIP commits) → app utilisable end-to-end
- **P8 Privacy Polish Beta** (~26 tasks) → ship-ready APK + Play Store internal

Total restant : ~50 tasks, ~5-7 jours dev solo + Claude.
