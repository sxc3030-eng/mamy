---
name: Mamy P2 Voice Capture COMPLETE 2026-05-03
description: P2 livré - 18 commits, APK builds avec native whisper.cpp, 59 tests pass. Ready for P3 LLM Structurer.
type: handoff
originSessionId: 68c203be-d6a4-40c4-a377-8b1fdf6049d8
---
**LIRE EN PREMIER prochaine session Mamy** (état au 2026-05-03 PM, post-marathon P1+P2 même jour).

## État live
- **Repo** : `D:\ComfyUI-Intel\mamy\` · **GitHub** : https://github.com/sxc3030-eng/mamy
- **Branch `main`** : HEAD `70ba1b5`, P2 mergé via `--no-ff`
- **Branches conservées** : `p1-foundation` (22 commits), `p2-voice-capture` (18 commits)
- **Tags** : `checkpoint/mamy-p1-complete-2026-05-03` + `checkpoint/mamy-p2-complete-2026-05-03`
- **APK debug** : `app-debug.apk` build green avec native whisper.cpp 3 ABIs

## Smoke test live (vérifié)
| Command | Résultat |
|---|---|
| `./gradlew :app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` | ✅ 67 total, 59 pass, 8 @Ignore (KeyStore), 0 fail |
| `./gradlew :app:externalNativeBuildDebug` | ✅ libmamy_whisper.so produite arm64-v8a + armeabi-v7a + x86_64 |

## Ce que P2 ship en code (18 commits)

### Pipeline vocale complète
- **Picovoice Porcupine** wake-word engine, modèle « MamY » custom (sensibilité tunable, charge depuis `app/src/main/assets/wakeword/mamy_*.ppn`)
- **AudioCapture** (`AudioRecord` 16 kHz mono PCM, `callbackFlow` lifecycle, `AtomicBoolean` stop)
- **VadProcessor** + **SimpleEnergyVad** (RMS-based, threshold ~200, pas de lib externe — JitPack webrtc-vad était unreachable 401)
- **whisper.cpp v1.7.2** vendoré à `app/src/main/cpp/whisper-cpp/` (~5 MB après pruning examples/tests/samples)
- **NDK 26.3.11579264** + **CMake 3.22.1** build natif 3 ABIs avec NEON FP16 flags pour arm64-v8a
- **JNI bridge** init/transcribe/free
- **WhisperEngineImpl** Kotlin API + first-run model downloader (whisper-tiny ~75 MB, sha256 vérifié)
- **IntentRouter stub** (P4 implémente la grammaire 10 intents)
- **CapturePipeline** orchestrateur : wake-word → AudioCapture → VadProcessor → WhisperEngine → IntentRouter, expose `Flow<CaptureEvent>`
- **MamYListenerService** wired : wake-word continu → on detection pause, run one capture, resume. Notif state reflète Idle/Recording/Transcribing.
- **Volume-up long-press** fallback (Activity-foreground V1) bypass wake-word
- **Permissions runtime** flow RECORD_AUDIO + POST_NOTIFICATIONS

### Hilt DI ajoutée
- `WakeWordModule` (PorcupineWakeWordEngine + WakeWordModelResolver + AccessKey via SecretsVault)
- `AudioModule` (AudioCapture singleton)
- `SttModule` (WhisperEngine + WhisperModel + ModelDownloader)

## Pas encore (P3 onwards)

- ❌ **LLM cloud BYOK calls** (Claude/GPT/Gemini) — P3
- ❌ **JSON structuration** depuis transcript Whisper — P3
- ❌ **Cost tracker** par provider — P3
- ❌ **TTS confirmation** post-capture — P3
- ❌ **10 voice intents** (capture/daily_brief/next_brief/...) — P4
- ❌ **Mémoire query** (promesses ouvertes, actions, person history) — P4
- ❌ **Calendar OAuth** + Google Calendar sync — P5
- ❌ **Briefing engine** (4 types + WorkManager) — P6
- ❌ **Real UI screens** (placeholders pour now) — P7
- ❌ **Export/wipe** + Play Store — P8

## Setup user TODO avant live test sur device

### Critique (sinon l'app crash au wake-word)
1. **Picovoice Console** : https://console.picovoice.ai/
   - Créer compte free
   - Section **Wake Word** → train custom phrase « MamY »
     - Modèle `English` → download → renommer `mamy_en.ppn`
     - Modèle `French` → download → renommer `mamy_fr.ppn`
   - Drop les 2 fichiers dans `D:\ComfyUI-Intel\mamy\app\src\main\assets\wakeword\` (le .gitkeep + README sont déjà là)
2. **AccessKey Picovoice** : Settings → copier l'AccessKey, sauver dans SecretsVault sous la clé `"picovoice_access_key"`. Pour V1 dev, on peut le hardcoder temporairement — préparer un mécanisme settings UI en P7.
3. **Connection internet** au premier lancement : whisper-tiny.bin se télécharge auto (~75 MB) depuis ggerganov repo. Sans internet first-run, transcription marchera pas.

### Optionnel
- `setx ANDROID_HOME` permanent (si pas déjà fait pour P1)
- Émulateur Pixel via Android Studio si pas de tel physique

## Pièges / déviations du plan P2

1. **JitPack `com.github.yuriy-budiyev:webrtc-vad-android` retourne 401** → remplacé par `SimpleEnergyVad` inline (RMS detector ~30 lignes). Threshold default 200, tunable via param.
2. **`@Volatile` pas valide sur local var** dans AudioCaptureImpl → `java.util.concurrent.atomic.AtomicBoolean`
3. **CMakeLists.txt manquait `ggml-aarch64.c`** (NEON intrinsics). Plus per-ABI compile flags ajoutés.
4. **`GGML_USE_OPENMP=0` define** retiré du cpp/cmake — code utilise `#ifdef`, donc toute valeur déclenche la branche OpenMP. Laisser undefined = correct way.
5. **AudioCaptureImplTest + WakeWordModelResolverTest** utilisaient `AndroidJUnit4` (dep classpath issue) → switchés à `RobolectricTestRunner`.
6. **VadProcessorTest** mélange Jupiter + JUnit 4 arg order → Jupiter pur (message en dernier).
7. **WhisperModel injection** : `WhisperEngineImpl(model: WhisperModel = WhisperModel.TINY)` — Dagger ne résout pas default param. Ajouté `@Provides fun provideWhisperModel() = WhisperModel.TINY` dans SttModule.

## Comment reprendre P3

1. Open Claude Code dans `D:\ComfyUI-Intel\mamy\` (mnemo voit le projet séparé)
2. `git checkout -b p3-llm-structurer` à partir de `main`
3. Lancer `superpowers:subagent-driven-development` sur `docs/superpowers/plans/2026-05-02-mamy-p3-llm-structurer.md`
4. P3 plan : 20 tasks, ~2-3 jours dev solo + Claude
   - BYOK Claude + GPT + Gemini providers (sealed interface + 3 impl)
   - Prompt FR/EN + JSON schema validation
   - LlmCostTracker (Room table, monthly aggregation)
   - LlmStructurer orchestrator
   - **Capture flow end-to-end** : wake-word → audio → STT → LLM → JSON → DB → TTS confirmation
   - TTS Android natif

## Statistiques projet (cumulatif)
- **40 commits** sur main (22 P1 + 18 P2)
- **~50 fichiers source Kotlin/C++/XML/CMake**
- **59 tests pass + 8 @Ignore** = 67 total
- **APK debug** ~62 MB (avec native .so 3 ABIs)
- **Repo size** ~12 MB (incluant whisper.cpp vendor)

## Plans restants
- P3 LLM Structurer (~20 tasks)
- P4 Voice Intents & Memory (~18 tasks)
- P5 Calendar Integration (~18 tasks)
- P6 Briefing Engine (~22 tasks)
- P7 UI Compose (~26 tasks)
- P8 Privacy Polish Beta (~26 tasks)

Total restant : ~130 tasks, ~2-3 mois dev.
