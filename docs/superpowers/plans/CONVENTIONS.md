# MamY — Conventions partagées par tous les sous-plans

**Lis ce fichier avant tout sous-plan.** Toutes les conventions techniques pour P1-P8.

## Package & nom
- **Package racine** : `com.mamy.android`
- **Application ID** : `com.mamy.android`
- **Display name** : `MamY` (ressource `app_name`)
- **GitHub** : `sxc3030-eng/mamy`
- **Module principal** : `app/`

## Gradle & SDK
- **Gradle Kotlin DSL** (`.kts`) partout, pas de Groovy
- **Min SDK** : 28 (Android 9.0)
- **Target SDK** : 35 (Android 15)
- **Compile SDK** : 35
- **Kotlin** : 2.0.21+
- **AGP** : 8.7+
- **Java target** : 17

## Dépendances pinnées (versions ref V1)

```kotlin
// libs.versions.toml entries
[versions]
kotlin = "2.0.21"
agp = "8.7.2"
compose-bom = "2024.12.01"
hilt = "2.52"
room = "2.6.1"
sqlcipher = "4.6.1"
datastore = "1.1.1"
coroutines = "1.9.0"
work = "2.10.0"
nav-compose = "2.8.4"
junit = "5.11.3"
mockk = "1.13.13"
robolectric = "4.14.1"
turbine = "1.2.0"
```

## Structure de dossiers (anchor pour tous les plans)

```
app/src/main/kotlin/com/mamy/android/
├── MamYApplication.kt          (Hilt @HiltAndroidApp entry point)
├── MainActivity.kt
├── ui/                         (P7 mainly)
│   ├── theme/
│   ├── nav/
│   └── screens/
├── domain/
│   ├── capture/                (P3, P4)
│   ├── briefing/               (P6)
│   ├── memory/                 (P4)
│   └── intent/                 (P4)
├── data/
│   ├── db/                     (P1)
│   │   ├── MamYDatabase.kt
│   │   ├── entity/
│   │   ├── dao/
│   │   └── converter/          (TypeConverters Instant↔Long, etc.)
│   ├── llm/                    (P3)
│   │   ├── LlmProvider.kt      (sealed interface)
│   │   ├── claude/
│   │   ├── openai/
│   │   └── gemini/
│   ├── stt/                    (P2)
│   │   ├── WhisperEngine.kt
│   │   └── jni/
│   ├── wakeword/               (P2)
│   │   └── PorcupineEngine.kt
│   ├── audio/                  (P2)
│   │   ├── AudioCapture.kt
│   │   └── VadProcessor.kt
│   ├── calendar/               (P5)
│   │   ├── google/
│   │   └── CalendarRepository.kt
│   ├── settings/               (P1)
│   ├── secrets/                (P1, BYOK keys)
│   └── tts/                    (P6)
├── service/
│   └── MamYListenerService.kt  (P1 skeleton, P2 wires audio)
├── di/                         (Hilt modules, par couche)
└── util/
```

## Tests
- **Unit** : JUnit 5 (Jupiter) + MockK + Robolectric (sans émulateur, rapide).
  Path : `app/src/test/kotlin/com/mamy/android/...`
- **Instrumented** : Compose UI test + Espresso + AndroidX Test.
  Path : `app/src/androidTest/kotlin/com/mamy/android/...`
- **Convention de nommage** : `<Class>Test.kt` (unit) vs `<Class>InstrumentedTest.kt` (instrumented)
- **Coverage minimal** : 70 % sur `data/` + `domain/`. Pas requis sur `ui/` V1.

## Discipline TDD
Chaque tâche du plan = bloc indivisible :
1. Write failing test (code dans le plan)
2. Run test, expect FAIL (commande exacte)
3. Write minimal implementation (code dans le plan)
4. Run test, expect PASS
5. Commit avec préfixe (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`)

**Aucune étape sans code complet** dans le plan. Pas de « TBD », « TODO », « add proper handling ».

## Commit conventions
```
feat: add <feature>
fix: <bug fix>
test: <test added>
refactor: <internal restructure, no behavior change>
chore: <build/deps/tooling>
docs: <docs only>
```

Footer auto pour cette session :
```
Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

## Branches
- **`main`** : tout V1 ship-ready
- **`p<N>-<name>`** : branche par sous-plan (ex `p1-foundation`, `p2-voice-capture`)
- Rebase sur `main` avant merge, squash si historique commit messy

## i18n
- **Strings** dans `res/values/strings.xml` (EN default) + `res/values-fr/strings.xml`
- **Pas de string hardcodé** dans les composables/services après P1 setup
- **Convention clé** : `screen_action_what` (ex : `settings_title`, `onboarding_btn_continue`)

## Commun à tous les plans
- Path absolu Windows partout (`D:/ComfyUI-Intel/mamy/...`)
- Tester sur émulateur Android Studio + APK side-load Pixel/OnePlus si dispo
- Avant chaque commit : `./gradlew test` (unit) + `./gradlew lint`
