---
name: Mamy P1 Foundation COMPLETE 2026-05-03
description: P1 livré - 22 commits, APK builds, 38 tests pass. Ready for P2 voice capture.
type: handoff
originSessionId: 68c203be-d6a4-40c4-a377-8b1fdf6049d8
---
**LIRE EN PREMIER prochaine session Mamy** (état au 2026-05-03 matin, post-marathon brainstorm + P1 build).

## État live
- **Repo** : `D:\ComfyUI-Intel\mamy\` · **GitHub** : https://github.com/sxc3030-eng/mamy (privé)
- **Branch `main`** : HEAD `9d8249e`, P1 Foundation merged via `--no-ff`
- **Branch `p1-foundation`** : conservée comme historique (22 commits TDD)
- **Tag** : `checkpoint/mamy-p1-complete-2026-05-03` poussé sur GitHub
- **APK debug** : `app-debug.apk` 58 MB, build vert

## Smoke test live (vérifié sur la machine du user)
| Command | Résultat |
|---|---|
| `./gradlew :app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` | ✅ 38 pass, 8 @Ignore, 0 fail |

8 tests @Ignore = KeystoreHelper + SecretsVault. Robolectric ne shim pas `AndroidKeyStore`. Ces tests seront promus en `androidTest` (instrumented) en P2 quand un émulateur est dispo.

## Ce que P1 ship (résumé technique)

### Stack opérationnel
- Gradle 8.10.2 + Kotlin 2.0.21 + Compose BOM 2024.12.01 + Hilt 2.52
- Min SDK 28 (Android 9.0), Target SDK 35, JVM 17
- Package : `com.mamy.android`

### Fonctionnel
- AndroidManifest : 7 permissions (RECORD_AUDIO, FOREGROUND_SERVICE_MICROPHONE, POST_NOTIFICATIONS, INTERNET, WAKE_LOCK, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE)
- `MamYApplication` @HiltAndroidApp · `MainActivity` @AndroidEntryPoint
- Material 3 theme avec dynamic color (Android 12+) + dark/light + big-text typography
- Nav Compose scaffold : 6 routes placeholder (Onboarding, ReportsList, PersonDetail, Actions, Settings, NetworkLog)
- i18n FR+EN dans `res/values-*/strings.xml`
- `KeystoreHelper` AES-256 GCM hardware-backed
- `SecretsVault` chiffre BYOK keys + génère 32-byte passphrase pour SQLCipher
- `MamYDatabase` + 8 entités + 8 DAOs avec SQLCipher (Person, Note, Action, Promise, Flag, Meeting, MeetingAttendee, Briefing)
- TypeConverters Instant↔Long, UUID↔String
- `SettingsRepository` DataStore : language, briefing time, LLM provider, privacy mode, wake-word sensitivity
- 3 Hilt DI modules : DatabaseModule + SecretsModule + SettingsModule
- `MamYListenerService` foreground notification skeleton, FOREGROUND_SERVICE_TYPE_MICROPHONE déclaré
- Launcher adaptive icon (lettre "M" emerald + voice dot orange) + notification icon

### Pas encore (P2 onwards)
- ❌ Wake-word detection (P2)
- ❌ Audio capture / VAD (P2)
- ❌ Whisper STT (P2)
- ❌ LLM BYOK calls (P3)
- ❌ Capture flow end-to-end (P3)
- ❌ 10 voice intents (P4)
- ❌ Calendar OAuth + sync (P5)
- ❌ Briefing engine + TTS (P6)
- ❌ Real UI screens (P7)
- ❌ Export/wipe + Play Store (P8)

## Bootstrap fait pendant la session
- Gradle 8.10.2 téléchargé à `/tmp/gradle-8.10.2/` (peut être supprimé, wrapper installé maintenant)
- Wrapper JAR généré : `gradle/wrapper/gradle-wrapper.jar`
- `local.properties` créé pointant vers `C:\Users\sxc_2\AppData\Local\Android\Sdk` (gitignored)
- Android SDK Platform 35 installé automatiquement par AGP

## Pièges / déviations du plan
1. **`@InstallIn` annotation a `CLASS` retention** → DiModulesTest simplifié pour ne tester que `@Module` (RUNTIME). Le full Hilt graph se vérifie en P2 via `@HiltAndroidTest` instrumented.
2. **Robolectric ne fournit pas AndroidKeyStore** → 8 tests Keystore/Vault @Ignore, à promouvoir en androidTest P2.
3. **Plan utilisait JUnit 4 imports + Jupiter arg order** dans `SecretsVaultTest` → corrigé (message en premier arg pour JUnit 4).
4. **MamYListenerService utilisait LifecycleService** → switché vers `Service` plain (lifecycle-service pas dans les deps T2). Ajouter dans P2 si besoin.
5. **Manifest référençait `mipmap/ic_launcher` + `ic_launcher_round`** → manquaient. J'ai créé adaptive icons (drawable foreground + background + mipmap-anydpi-v26 wrappers).
6. **Plan prévoyait test `SettingsRepositoryTest` avec mix JUnit4/Jupiter** → réécrit en Jupiter pur.

## Comment reprendre P2
1. Ouvrir Claude Code dans `D:\ComfyUI-Intel\mamy\` (cwd) → mnemo voit le projet comme entrée séparée
2. Lancer skill `superpowers:subagent-driven-development` sur `docs/superpowers/plans/2026-05-02-mamy-p2-voice-capture.md`
3. Branche : `git checkout -b p2-voice-capture` à partir de `main`
4. P2 plan : 21 tasks (~2849 lignes TDD)
   - Picovoice Porcupine SDK + custom "MamY" wake-word model (à entraîner via Picovoice Console — étape web ~10 min)
   - AudioRecord 16kHz mono PCM
   - WebRTC VAD lib
   - whisper.cpp NDK + JNI
   - Pipeline wired dans MamYListenerService

## Setup user à faire avant P2 (one-shot)
- [ ] Set `ANDROID_HOME` env var permanent : `setx ANDROID_HOME "C:\Users\sxc_2\AppData\Local\Android\Sdk"` (sinon gradle réclame à chaque build)
- [ ] Optionnel : installer Android Studio pour avoir l'émulateur (sinon side-load APK sur téléphone Android physique via USB debugging)
- [ ] Créer compte Picovoice Console + entraîner le modèle wake-word « MamY » (FR + EN, 2 fichiers .ppn) — voir P2 plan Task 1
- [ ] Vérifier la branche locale : `git -C D:/ComfyUI-Intel/mamy log --oneline -5` → doit montrer `9d8249e merge: P1 Foundation complete...`

## Quick test sur device
```
adb install D:\ComfyUI-Intel\mamy\app\build\outputs\apk\debug\app-debug.apk
```
L'app lance, montre `ReportsListScreen` (placeholder « Your team » / « Ton équipe » selon langue système). En P1 elle ne capture rien encore — P2 ajoute le wake-word.

## Plans restants (à exécuter sub-plan par sub-plan)
- P2 Voice Capture Pipeline (~21 tasks)
- P3 LLM Structurer (~20 tasks)
- P4 Voice Intents & Memory (~18 tasks)
- P5 Calendar Integration (~18 tasks)
- P6 Briefing Engine (~22 tasks)
- P7 UI Compose (~26 tasks)
- P8 Privacy Polish Beta (~26 tasks)

Total restant : ~150 tasks. Avec dispatch agents parallèles pattern (entity batch sur P3, screens batch sur P7), réaliste en ~3 mois solo + Claude.
