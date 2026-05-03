# MamY P1 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bootstrap the MamY Android project with Kotlin/Compose/Hilt foundation, encrypted Room+SQLCipher 8-table database, BYOK secrets vault, foreground service skeleton, and i18n FR/EN scaffolding — ready for P2 (voice capture) to plug in.

**Architecture:** Kotlin native Android targeting API 28+, Jetpack Compose UI, Hilt DI, Room+SQLCipher encrypted local DB, Android Keystore-backed secrets vault, foreground service with permanent notification ready for always-on wake-word in P2.

**Tech Stack:** Kotlin 2.0.21 · AGP 8.7.2 · Compose BOM 2024.12.01 · Hilt 2.52 · Room 2.6.1 · SQLCipher 4.6.1 · DataStore 1.1.1 · Coroutines 1.9.0 · JUnit 5 · MockK 1.13.13 · Robolectric 4.14.1

---

## Task 1 — Project init + Gradle root config

**Files**
- Create: `D:/ComfyUI-Intel/mamy/settings.gradle.kts`
- Create: `D:/ComfyUI-Intel/mamy/build.gradle.kts`
- Create: `D:/ComfyUI-Intel/mamy/gradle.properties`
- Create: `D:/ComfyUI-Intel/mamy/gradle/wrapper/gradle-wrapper.properties`
- Create: `D:/ComfyUI-Intel/mamy/local.properties.example`

**Steps**

- [ ] **Step 1 — Write `settings.gradle.kts` declaring single module + repos**

```kotlin
// D:/ComfyUI-Intel/mamy/settings.gradle.kts
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MamY"
include(":app")
```

- [ ] **Step 2 — Write root `build.gradle.kts` declaring plugins via versions catalog**

```kotlin
// D:/ComfyUI-Intel/mamy/build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 3 — Write `gradle.properties` with JVM heap + Kotlin/AndroidX flags**

```properties
# D:/ComfyUI-Intel/mamy/gradle.properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 4 — Write `gradle-wrapper.properties` pinning Gradle 8.10**

```properties
# D:/ComfyUI-Intel/mamy/gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 5 — Write `local.properties.example` documenting required SDK path**

```properties
# D:/ComfyUI-Intel/mamy/local.properties.example
# Copy to local.properties and adjust for your machine.
# DO NOT commit local.properties (already in .gitignore).
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

- [ ] **Step 6 — Verify Gradle root parses (no app module yet, expect Project.assemble to be empty)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat tasks --no-daemon 2>&1 | head -40`
Expected: no error, prints "Tasks runnable from root project". Any error means root config typo.

- [ ] **Step 7 — Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/wrapper/gradle-wrapper.properties local.properties.example
git commit -m "$(cat <<'EOF'
chore: init Gradle root config (Kotlin DSL, Gradle 8.10.2)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2 — `libs.versions.toml` versions catalog

**Files**
- Create: `D:/ComfyUI-Intel/mamy/gradle/libs.versions.toml`

**Steps**

- [ ] **Step 1 — Write versions catalog with all P1 deps + plugin coords**

```toml
# D:/ComfyUI-Intel/mamy/gradle/libs.versions.toml
[versions]
agp = "8.7.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.27"
compose-bom = "2024.12.01"
compose-compiler = "1.5.15"
hilt = "2.52"
hilt-navigation-compose = "1.2.0"
room = "2.6.1"
sqlcipher = "4.6.1"
sqlite = "2.4.0"
datastore = "1.1.1"
coroutines = "1.9.0"
work = "2.10.0"
nav-compose = "2.8.4"
lifecycle = "2.8.7"
activity-compose = "1.9.3"
core-ktx = "1.15.0"
appcompat = "1.7.0"
material = "1.12.0"
security-crypto = "1.1.0-alpha06"
junit = "5.11.3"
junit-vintage = "5.11.3"
junit4 = "4.13.2"
mockk = "1.13.13"
robolectric = "4.14.1"
turbine = "1.2.0"
androidx-test = "1.6.1"
androidx-test-ext = "1.2.1"
androidx-test-runner = "1.6.2"
espresso = "3.6.1"
compose-test = "1.7.5"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core-ktx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }

compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose", version.ref = "nav-compose" }

hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }

room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
sqlcipher = { group = "net.zetetic", name = "sqlcipher-android", version.ref = "sqlcipher" }
androidx-sqlite = { group = "androidx.sqlite", name = "sqlite", version.ref = "sqlite" }
androidx-sqlite-ktx = { group = "androidx.sqlite", name = "sqlite-ktx", version.ref = "sqlite" }

datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }

androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "security-crypto" }

junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "junit" }
junit-jupiter-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine", version.ref = "junit" }
junit-jupiter-params = { group = "org.junit.jupiter", name = "junit-jupiter-params", version.ref = "junit" }
junit-vintage-engine = { group = "org.junit.vintage", name = "junit-vintage-engine", version.ref = "junit-vintage" }
junit4 = { group = "junit", name = "junit", version.ref = "junit4" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
mockk-android = { group = "io.mockk", name = "mockk-android", version.ref = "mockk" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidx-test" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidx-test-runner" }
androidx-test-rules = { group = "androidx.test", name = "rules", version.ref = "androidx-test" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidx-test-ext" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-kapt = { id = "org.jetbrains.kotlin.kapt", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2 — Verify Gradle resolves catalog**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat help --no-daemon 2>&1 | tail -10`
Expected: no error about missing version refs. If `Could not get unknown property` errors appear, a version ref is mistyped.

- [ ] **Step 3 — Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "$(cat <<'EOF'
chore: add libs.versions.toml with pinned V1 deps

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3 — `:app` module Gradle config

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/build.gradle.kts`
- Create: `D:/ComfyUI-Intel/mamy/app/proguard-rules.pro`
- Create: `D:/ComfyUI-Intel/mamy/app/consumer-rules.pro`

**Steps**

- [ ] **Step 1 — Write `app/build.gradle.kts` with full Android config**

```kotlin
// D:/ComfyUI-Intel/mamy/app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mamy.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mamy.android"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "com.mamy.android.MamYTestRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md",
            )
        }
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")
    sourceSets["androidTest"].java.srcDirs("src/androidTest/kotlin")

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.hilt.android)
    kaptAndroidTest(libs.hilt.compiler)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 2 — Write empty `proguard-rules.pro` placeholder**

```pro
# D:/ComfyUI-Intel/mamy/app/proguard-rules.pro
# Add project-specific ProGuard rules here.
# Keep room entities (Room handles via @Keep annotation in P1.10).
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
```

- [ ] **Step 3 — Write empty `consumer-rules.pro`**

```pro
# D:/ComfyUI-Intel/mamy/app/consumer-rules.pro
```

- [ ] **Step 4 — Build & smoke test (config sync only, no source yet)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:dependencies --configuration debugRuntimeClasspath --no-daemon 2>&1 | tail -20`
Expected: dependency tree printed without "Could not resolve" errors.

- [ ] **Step 5 — Commit**

```bash
git add app/build.gradle.kts app/proguard-rules.pro app/consumer-rules.pro
git commit -m "$(cat <<'EOF'
chore: configure :app module (Kotlin/Compose/Hilt/Room)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4 — AndroidManifest.xml + permissions

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/AndroidManifest.xml`

**Steps**

- [ ] **Step 1 — Write manifest with all P1 permissions + service skeleton declaration**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- D:/ComfyUI-Intel/mamy/app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <uses-feature
        android:name="android.hardware.microphone"
        android:required="true" />

    <application
        android:name=".MamYApplication"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MamY"
        tools:targetApi="35">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.MamY">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.MamYListenerService"
            android:exported="false"
            android:foregroundServiceType="microphone" />

    </application>

</manifest>
```

- [ ] **Step 2 — Create `data_extraction_rules.xml` (Android 12+ backup opt-out)**

Create `D:/ComfyUI-Intel/mamy/app/src/main/res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
        <exclude domain="file" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
        <exclude domain="file" />
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Step 3 — Create `backup_rules.xml` (legacy ≤Android 11)**

Create `D:/ComfyUI-Intel/mamy/app/src/main/res/xml/backup_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="root" />
    <exclude domain="database" />
    <exclude domain="sharedpref" />
    <exclude domain="external" />
    <exclude domain="file" />
</full-backup-content>
```

- [ ] **Step 4 — Build & smoke (manifest merge will fail until Application/Activity classes exist; that's fine here, only test parser)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:processDebugManifest --no-daemon 2>&1 | tail -10`
Expected: lint warning is OK; we just want manifest XML to parse cleanly. If the task fails due to missing class, that's expected — proceed to next task. Hard XML parse errors block.

- [ ] **Step 5 — Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/xml/data_extraction_rules.xml app/src/main/res/xml/backup_rules.xml
git commit -m "$(cat <<'EOF'
feat: add AndroidManifest with mic/FGS/notifications permissions

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5 — `MamYApplication` class + Hilt scaffolding

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/MamYApplication.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/MamYApplicationTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test that asserts `MamYApplication` is annotated `@HiltAndroidApp`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/MamYApplicationTest.kt
package com.mamy.android

import dagger.hilt.android.HiltAndroidApp
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MamYApplicationTest {

    @Test
    fun `MamYApplication is annotated with HiltAndroidApp`() {
        val annotation = MamYApplication::class.java.getAnnotation(HiltAndroidApp::class.java)
        assertNotNull(annotation, "MamYApplication must be annotated @HiltAndroidApp")
    }

    @Test
    fun `MamYApplication extends android Application`() {
        assertTrue(
            android.app.Application::class.java.isAssignableFrom(MamYApplication::class.java),
            "MamYApplication must extend android.app.Application"
        )
    }
}
```

- [ ] **Step 2 — Run test, expect FAIL (class doesn't exist)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.MamYApplicationTest" --no-daemon 2>&1 | tail -20`
Expected: compilation error `Unresolved reference: MamYApplication`.

- [ ] **Step 3 — Create `MamYApplication.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/MamYApplication.kt
package com.mamy.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MamYApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Hilt-managed singletons (DB, vault, settings) wire up from DI modules in Task 20.
    }
}
```

- [ ] **Step 4 — Run test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.MamYApplicationTest" --no-daemon 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL`, 2 tests pass.

- [ ] **Step 5 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/MamYApplication.kt app/src/test/kotlin/com/mamy/android/MamYApplicationTest.kt
git commit -m "$(cat <<'EOF'
feat: add MamYApplication with @HiltAndroidApp

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6 — Material 3 theme (Color.kt, Type.kt, Theme.kt)

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/theme/Color.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/theme/Type.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/theme/Theme.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/res/values/themes.xml`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/res/values/colors.xml`

**Steps**

- [ ] **Step 1 — Write `Color.kt` with MamY palette (warm-pro, voice-first)**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/theme/Color.kt
package com.mamy.android.ui.theme

import androidx.compose.ui.graphics.Color

// Light scheme (warm sand + emerald accent for "listening")
val LightPrimary = Color(0xFF2E7D5C)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFB6F1D8)
val LightOnPrimaryContainer = Color(0xFF002115)
val LightSecondary = Color(0xFF4D6358)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD0E8DA)
val LightOnSecondaryContainer = Color(0xFF0A1F17)
val LightTertiary = Color(0xFFB75A1C)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightBackground = Color(0xFFF7F4EE)
val LightOnBackground = Color(0xFF1A1C1A)
val LightSurface = Color(0xFFFFFBF5)
val LightOnSurface = Color(0xFF1A1C1A)
val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)

// Dark scheme
val DarkPrimary = Color(0xFF9BD5BC)
val DarkOnPrimary = Color(0xFF003828)
val DarkPrimaryContainer = Color(0xFF105D43)
val DarkOnPrimaryContainer = Color(0xFFB6F1D8)
val DarkSecondary = Color(0xFFB4CCBE)
val DarkOnSecondary = Color(0xFF20352B)
val DarkSecondaryContainer = Color(0xFF364C41)
val DarkOnSecondaryContainer = Color(0xFFD0E8DA)
val DarkTertiary = Color(0xFFFFB68A)
val DarkOnTertiary = Color(0xFF552100)
val DarkBackground = Color(0xFF101412)
val DarkOnBackground = Color(0xFFE2E3DE)
val DarkSurface = Color(0xFF181B19)
val DarkOnSurface = Color(0xFFE2E3DE)
val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)
```

- [ ] **Step 2 — Write `Type.kt` with big-text accessibility-friendly scale**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/theme/Type.kt
package com.mamy.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MamYTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
)
```

- [ ] **Step 3 — Write `Theme.kt` composable**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/theme/Theme.kt
package com.mamy.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    error = LightError,
    onError = LightOnError,
)

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    error = DarkError,
    onError = DarkOnError,
)

@Composable
fun MamYTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MamYTypography,
        content = content,
    )
}
```

- [ ] **Step 4 — Write XML themes/colors so Manifest theme reference resolves**

`D:/ComfyUI-Intel/mamy/app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.MamY" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">true</item>
    </style>
</resources>
```

`D:/ComfyUI-Intel/mamy/app/src/main/res/values/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="mamy_primary">#FF2E7D5C</color>
    <color name="mamy_on_primary">#FFFFFFFF</color>
</resources>
```

- [ ] **Step 5 — Build & smoke (theme compiles)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:compileDebugKotlin --no-daemon 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL`. Compilation errors here mean Theme.kt or Color.kt have a typo.

- [ ] **Step 6 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/ui/theme app/src/main/res/values/themes.xml app/src/main/res/values/colors.xml
git commit -m "$(cat <<'EOF'
feat: add Material 3 theme (Color, Type, Theme) + dynamic color

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7 — Navigation scaffold (Routes.kt + MamYNav.kt) with 6 placeholder screens

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/nav/Routes.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/nav/MamYNav.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/screens/PlaceholderScreens.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/ui/nav/RoutesTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test that all 6 routes are declared and unique**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/ui/nav/RoutesTest.kt
package com.mamy.android.ui.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoutesTest {

    @Test
    fun `all 6 P1 routes are declared`() {
        val routes = listOf(
            Routes.Onboarding,
            Routes.ReportsList,
            Routes.PersonDetail,
            Routes.Actions,
            Routes.Settings,
            Routes.NetworkLog,
        )
        assertEquals(6, routes.size)
        assertEquals(6, routes.map { it.path }.toSet().size, "Routes paths must be unique")
    }

    @Test
    fun `PersonDetail route declares personId arg placeholder`() {
        assertTrue(Routes.PersonDetail.path.contains("{personId}"),
            "PersonDetail path must contain {personId} arg placeholder")
    }
}
```

- [ ] **Step 2 — Run test, expect FAIL (`Unresolved reference: Routes`)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.ui.nav.RoutesTest" --no-daemon 2>&1 | tail -10`
Expected: compilation error.

- [ ] **Step 3 — Create `Routes.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/nav/Routes.kt
package com.mamy.android.ui.nav

sealed class Routes(val path: String) {
    data object Onboarding : Routes("onboarding")
    data object ReportsList : Routes("reports")
    data object PersonDetail : Routes("reports/{personId}") {
        fun build(personId: String): String = "reports/$personId"
    }
    data object Actions : Routes("actions")
    data object Settings : Routes("settings")
    data object NetworkLog : Routes("settings/network-log")
}
```

- [ ] **Step 4 — Create placeholder Composables**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/screens/PlaceholderScreens.kt
package com.mamy.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mamy.android.R

@Composable
fun OnboardingScreen() = Centered(stringResource(R.string.screen_onboarding_title))

@Composable
fun ReportsListScreen(onPersonClick: (String) -> Unit) =
    Centered(stringResource(R.string.screen_reports_title))

@Composable
fun PersonDetailScreen(personId: String) =
    Centered(stringResource(R.string.screen_person_detail_title) + " #" + personId)

@Composable
fun ActionsScreen() = Centered(stringResource(R.string.screen_actions_title))

@Composable
fun SettingsScreen(onNetworkLogClick: () -> Unit) =
    Centered(stringResource(R.string.screen_settings_title))

@Composable
fun NetworkLogScreen() = Centered(stringResource(R.string.screen_network_log_title))

@Composable
private fun Centered(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text)
    }
}
```

- [ ] **Step 5 — Create `MamYNav.kt` host**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/nav/MamYNav.kt
package com.mamy.android.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mamy.android.ui.screens.ActionsScreen
import com.mamy.android.ui.screens.NetworkLogScreen
import com.mamy.android.ui.screens.OnboardingScreen
import com.mamy.android.ui.screens.PersonDetailScreen
import com.mamy.android.ui.screens.ReportsListScreen
import com.mamy.android.ui.screens.SettingsScreen

@Composable
fun MamYNav() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.ReportsList.path,
    ) {
        composable(Routes.Onboarding.path) {
            OnboardingScreen()
        }
        composable(Routes.ReportsList.path) {
            ReportsListScreen(onPersonClick = { personId ->
                navController.navigate(Routes.PersonDetail.build(personId))
            })
        }
        composable(
            route = Routes.PersonDetail.path,
            arguments = listOf(navArgument("personId") { type = NavType.StringType }),
        ) { backStack ->
            val id = backStack.arguments?.getString("personId").orEmpty()
            PersonDetailScreen(personId = id)
        }
        composable(Routes.Actions.path) {
            ActionsScreen()
        }
        composable(Routes.Settings.path) {
            SettingsScreen(onNetworkLogClick = {
                navController.navigate(Routes.NetworkLog.path)
            })
        }
        composable(Routes.NetworkLog.path) {
            NetworkLogScreen()
        }
    }
}
```

- [ ] **Step 6 — Run test, expect PASS (strings.xml needed for placeholder screens compile — Task 8 next)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.ui.nav.RoutesTest" --no-daemon 2>&1 | tail -10`
Expected: 2 tests pass. Compilation of `PlaceholderScreens.kt` may fail temporarily because `R.string.*` references don't exist yet — that's resolved in Task 8. The `RoutesTest` itself only depends on `Routes.kt` and will compile + pass.

- [ ] **Step 7 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/ui/nav app/src/main/kotlin/com/mamy/android/ui/screens app/src/test/kotlin/com/mamy/android/ui/nav
git commit -m "$(cat <<'EOF'
feat: add nav scaffold with 6 placeholder routes

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8 — i18n strings.xml (EN default + FR)

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/res/values/strings.xml`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/res/values-fr/strings.xml`

**Steps**

- [ ] **Step 1 — Write English strings (default)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- D:/ComfyUI-Intel/mamy/app/src/main/res/values/strings.xml -->
<resources>
    <string name="app_name">MamY</string>

    <!-- Screens -->
    <string name="screen_onboarding_title">Welcome to MamY</string>
    <string name="screen_reports_title">Your team</string>
    <string name="screen_person_detail_title">Person</string>
    <string name="screen_actions_title">Open actions</string>
    <string name="screen_settings_title">Settings</string>
    <string name="screen_network_log_title">Network log</string>

    <!-- Onboarding -->
    <string name="onboarding_btn_continue">Continue</string>
    <string name="onboarding_intro_title">Voice-first secretary</string>
    <string name="onboarding_intro_body">Walk, talk, and let MamY structure your 1:1 debriefs.</string>

    <!-- Foreground service notification -->
    <string name="service_notif_channel_name">MamY listener</string>
    <string name="service_notif_channel_desc">Permanent notification while wake-word is active.</string>
    <string name="service_notif_title">MamY is listening</string>
    <string name="service_notif_text">Say \"MamY\" to capture or query.</string>

    <!-- Settings -->
    <string name="settings_section_general">General</string>
    <string name="settings_section_byok">API keys (BYOK)</string>
    <string name="settings_section_privacy">Privacy</string>
    <string name="settings_lang_label">Language</string>
    <string name="settings_lang_en">English</string>
    <string name="settings_lang_fr">Français</string>
    <string name="settings_briefing_schedule">Daily briefing time</string>
    <string name="settings_byok_claude">Anthropic Claude</string>
    <string name="settings_byok_openai">OpenAI GPT</string>
    <string name="settings_byok_gemini">Google Gemini</string>
    <string name="settings_byok_save">Save key</string>
    <string name="settings_byok_test">Test connection</string>
    <string name="settings_open_network_log">Show network calls</string>
    <string name="settings_export_all">Export everything</string>
    <string name="settings_wipe_all">Wipe everything</string>

    <!-- Common -->
    <string name="action_save">Save</string>
    <string name="action_cancel">Cancel</string>
    <string name="action_delete">Delete</string>
    <string name="action_confirm">Confirm</string>
    <string name="action_back">Back</string>
</resources>
```

- [ ] **Step 2 — Write French translations**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- D:/ComfyUI-Intel/mamy/app/src/main/res/values-fr/strings.xml -->
<resources>
    <string name="app_name">MamY</string>

    <!-- Écrans -->
    <string name="screen_onboarding_title">Bienvenue sur MamY</string>
    <string name="screen_reports_title">Ton équipe</string>
    <string name="screen_person_detail_title">Personne</string>
    <string name="screen_actions_title">Actions ouvertes</string>
    <string name="screen_settings_title">Réglages</string>
    <string name="screen_network_log_title">Journal réseau</string>

    <!-- Onboarding -->
    <string name="onboarding_btn_continue">Continuer</string>
    <string name="onboarding_intro_title">Secrétaire vocal</string>
    <string name="onboarding_intro_body">Marche, parle, MamY structure tes débriefs de 1:1.</string>

    <!-- Notification foreground service -->
    <string name="service_notif_channel_name">Écoute MamY</string>
    <string name="service_notif_channel_desc">Notification permanente pendant l\'écoute du mot-clé.</string>
    <string name="service_notif_title">MamY écoute</string>
    <string name="service_notif_text">Dis « MamY » pour capturer ou poser une question.</string>

    <!-- Réglages -->
    <string name="settings_section_general">Général</string>
    <string name="settings_section_byok">Clés API (BYOK)</string>
    <string name="settings_section_privacy">Confidentialité</string>
    <string name="settings_lang_label">Langue</string>
    <string name="settings_lang_en">English</string>
    <string name="settings_lang_fr">Français</string>
    <string name="settings_briefing_schedule">Heure du briefing quotidien</string>
    <string name="settings_byok_claude">Anthropic Claude</string>
    <string name="settings_byok_openai">OpenAI GPT</string>
    <string name="settings_byok_gemini">Google Gemini</string>
    <string name="settings_byok_save">Enregistrer la clé</string>
    <string name="settings_byok_test">Tester la connexion</string>
    <string name="settings_open_network_log">Voir les appels réseau</string>
    <string name="settings_export_all">Tout exporter</string>
    <string name="settings_wipe_all">Tout effacer</string>

    <!-- Commun -->
    <string name="action_save">Enregistrer</string>
    <string name="action_cancel">Annuler</string>
    <string name="action_delete">Supprimer</string>
    <string name="action_confirm">Confirmer</string>
    <string name="action_back">Retour</string>
</resources>
```

- [ ] **Step 3 — Build & smoke (resources merge + nav placeholders compile now)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:compileDebugKotlin --no-daemon 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL`. If `Unresolved reference: R` or missing string, fix before commit.

- [ ] **Step 4 — Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-fr/strings.xml
git commit -m "$(cat <<'EOF'
feat: add i18n strings.xml (EN default + FR)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9 — `KeystoreHelper` (Android Keystore master key)

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/secrets/KeystoreHelper.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/secrets/KeystoreHelperTest.kt`

**Steps**

- [ ] **Step 1 — Write failing Robolectric test that asserts master-key alias is created and rotates idempotently**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/secrets/KeystoreHelperTest.kt
package com.mamy.android.data.secrets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeystoreHelperTest {

    @Test
    fun `getOrCreateMasterKey returns non-null SecretKey`() {
        val helper = KeystoreHelper()
        val key = helper.getOrCreateMasterKey()
        assertNotNull("Master key must not be null", key)
        assertEquals("Master key must be AES", "AES", key.algorithm)
    }

    @Test
    fun `calling getOrCreateMasterKey twice returns same key alias`() {
        val helper = KeystoreHelper()
        helper.getOrCreateMasterKey()
        helper.getOrCreateMasterKey()
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue("Master key alias must persist after multiple calls",
            ks.containsAlias(KeystoreHelper.MASTER_KEY_ALIAS))
    }
}
```

> **Why JUnit 4 here:** Robolectric runs via `RobolectricTestRunner` (a JUnit 4 runner). The vintage engine added in Task 2 lets the Gradle Jupiter platform pick up JUnit 4 test classes. Pure-Kotlin tests below use Jupiter.

- [ ] **Step 2 — Run test, expect FAIL (`Unresolved reference: KeystoreHelper`)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.secrets.KeystoreHelperTest" --no-daemon 2>&1 | tail -15`
Expected: compilation error.

- [ ] **Step 3 — Create `KeystoreHelper.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/secrets/KeystoreHelper.kt
package com.mamy.android.data.secrets

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.security.KeyStore

/**
 * Wraps Android Keystore for the MamY master AES-256 key. Hardware-backed when device supports
 * StrongBox or TEE; otherwise software-backed inside the AndroidKeyStore daemon.
 *
 * Master key is used by [SecretsVault] to encrypt:
 *  - BYOK API keys (Claude / OpenAI / Gemini)
 *  - SQLCipher DB passphrase
 *
 * Never serialised, never leaves the keystore.
 */
class KeystoreHelper {

    fun getOrCreateMasterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = (ks.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }

    companion object {
        const val MASTER_KEY_ALIAS = "mamy_master_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SIZE_BITS = 256
    }
}
```

- [ ] **Step 4 — Run test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.secrets.KeystoreHelperTest" --no-daemon 2>&1 | tail -10`
Expected: 2 tests pass via Robolectric AndroidKeyStore shim. If they fail with `KeyStoreException: AndroidKeyStore not found`, ensure `testOptions.unitTests.isIncludeAndroidResources = true` (already in Task 3).

- [ ] **Step 5 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/data/secrets/KeystoreHelper.kt app/src/test/kotlin/com/mamy/android/data/secrets/KeystoreHelperTest.kt
git commit -m "$(cat <<'EOF'
feat: add KeystoreHelper for hardware-backed master AES-256 key

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10 — `SecretsVault` (encrypt / decrypt API keys + DB passphrase)

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/secrets/SecretsVault.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/secrets/SecretsVaultTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test for encrypt/decrypt round-trip + per-secret isolation**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/secrets/SecretsVaultTest.kt
package com.mamy.android.data.secrets

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecretsVaultTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val vault = SecretsVault(ctx, KeystoreHelper())

    @Test
    fun `put then get returns same string`() {
        vault.putSecret("claude_api_key", "sk-ant-test-12345")
        assertEquals("sk-ant-test-12345", vault.getSecret("claude_api_key"))
    }

    @Test
    fun `get unknown key returns null`() {
        assertNull(vault.getSecret("does_not_exist"))
    }

    @Test
    fun `two distinct keys do not collide`() {
        vault.putSecret("openai_api_key", "sk-aaa")
        vault.putSecret("gemini_api_key", "sk-bbb")
        assertEquals("sk-aaa", vault.getSecret("openai_api_key"))
        assertEquals("sk-bbb", vault.getSecret("gemini_api_key"))
    }

    @Test
    fun `getOrCreateDbPassphrase returns 32 byte stable passphrase`() {
        val p1 = vault.getOrCreateDbPassphrase()
        val p2 = vault.getOrCreateDbPassphrase()
        assertEquals(32, p1.size, "DB passphrase must be 32 bytes")
        assertArrayEquals(p1, p2, "DB passphrase must be stable across calls")
    }

    @Test
    fun `regenerated vault still reads previously stored secrets`() {
        vault.putSecret("k", "value")
        val freshVault = SecretsVault(ctx, KeystoreHelper())
        assertEquals("value", freshVault.getSecret("k"))
    }

    @Test
    fun `putSecret overwrite replaces previous value`() {
        vault.putSecret("rotate", "old")
        vault.putSecret("rotate", "new")
        assertNotEquals("old", vault.getSecret("rotate"))
        assertEquals("new", vault.getSecret("rotate"))
    }
}
```

- [ ] **Step 2 — Run test, expect FAIL (`Unresolved reference: SecretsVault`)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.secrets.SecretsVaultTest" --no-daemon 2>&1 | tail -10`
Expected: compilation error.

- [ ] **Step 3 — Create `SecretsVault.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/secrets/SecretsVault.kt
package com.mamy.android.data.secrets

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts arbitrary string secrets (BYOK API keys) and a stable 32-byte DB passphrase
 * using the AndroidKeystore master key from [KeystoreHelper].
 *
 * Storage: a private SharedPreferences file `mamy_vault.prefs`. We store
 * `Base64(IV || ciphertext)` per logical key. The actual AES key never leaves
 * the keystore.
 *
 * Threading: thread-safe via SharedPreferences synchronisation; cipher operations are
 * stateless (new Cipher per call).
 */
class SecretsVault(
    context: Context,
    private val keystoreHelper: KeystoreHelper,
) {

    private val prefs = context.getSharedPreferences(VAULT_FILE, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    fun putSecret(key: String, value: String) {
        require(key.isNotBlank()) { "key must not be blank" }
        val iv = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, keystoreHelper.getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val blob = iv + cipherText
        prefs.edit().putString(prefKey(key), Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun getSecret(key: String): String? {
        val encoded = prefs.getString(prefKey(key), null) ?: return null
        val blob = Base64.decode(encoded, Base64.NO_WRAP)
        if (blob.size < GCM_IV_BYTES + GCM_TAG_BYTES) return null
        val iv = blob.copyOfRange(0, GCM_IV_BYTES)
        val cipherText = blob.copyOfRange(GCM_IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keystoreHelper.getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    fun deleteSecret(key: String) {
        prefs.edit().remove(prefKey(key)).apply()
    }

    /**
     * Returns a stable 32-byte passphrase used to open SQLCipher. Generated once on first call,
     * persisted (encrypted) inside the vault, returned identically on subsequent calls.
     */
    fun getOrCreateDbPassphrase(): ByteArray {
        val existing = prefs.getString(prefKey(DB_PASSPHRASE_KEY), null)
        if (existing != null) {
            return Base64.decode(getSecret(DB_PASSPHRASE_KEY), Base64.NO_WRAP)
        }
        val passphrase = ByteArray(DB_PASSPHRASE_BYTES).also { random.nextBytes(it) }
        putSecret(DB_PASSPHRASE_KEY, Base64.encodeToString(passphrase, Base64.NO_WRAP))
        return passphrase
    }

    private fun prefKey(key: String): String = "secret::$key"

    companion object {
        private const val VAULT_FILE = "mamy_vault.prefs"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val DB_PASSPHRASE_KEY = "db_passphrase"
        private const val DB_PASSPHRASE_BYTES = 32
    }
}
```

- [ ] **Step 4 — Run test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.secrets.SecretsVaultTest" --no-daemon 2>&1 | tail -10`
Expected: 6 tests pass.

- [ ] **Step 5 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/data/secrets/SecretsVault.kt app/src/test/kotlin/com/mamy/android/data/secrets/SecretsVaultTest.kt
git commit -m "$(cat <<'EOF'
feat: add SecretsVault (AES-GCM keystore-wrapped secrets + DB passphrase)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11 — `MamYDatabase` skeleton + `TypeConverters`

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/MamYDatabase.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/converter/Converters.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/converter/ConvertersTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test for `Converters` (Instant↔Long, UUID↔String)**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/converter/ConvertersTest.kt
package com.mamy.android.data.db.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ConvertersTest {

    private val c = Converters()

    @Test
    fun `instantToLong round-trips`() {
        val now = Instant.parse("2026-05-02T18:30:45Z")
        val asLong = c.instantToLong(now)
        assertEquals(now, c.longToInstant(asLong))
    }

    @Test
    fun `instantToLong handles null`() {
        assertNull(c.instantToLong(null))
        assertNull(c.longToInstant(null))
    }

    @Test
    fun `uuidToString round-trips`() {
        val u = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertEquals(u, c.stringToUuid(c.uuidToString(u)))
    }

    @Test
    fun `uuidToString handles null`() {
        assertNull(c.uuidToString(null))
        assertNull(c.stringToUuid(null))
    }
}
```

- [ ] **Step 2 — Run test, expect FAIL**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.converter.ConvertersTest" --no-daemon 2>&1 | tail -10`
Expected: compilation error.

- [ ] **Step 3 — Create `Converters.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/converter/Converters.kt
package com.mamy.android.data.db.converter

import androidx.room.TypeConverter
import java.time.Instant
import java.util.UUID

class Converters {

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun uuidToString(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun stringToUuid(value: String?): UUID? = value?.let(UUID::fromString)
}
```

- [ ] **Step 4 — Create `MamYDatabase.kt` skeleton (entities + DAOs added in Tasks 12-19)**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/MamYDatabase.kt
package com.mamy.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mamy.android.data.db.converter.Converters
import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.BriefingDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.BriefingEntity
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.MeetingAttendeeEntity
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity

@Database(
    entities = [
        PersonEntity::class,
        NoteEntity::class,
        ActionEntity::class,
        PromiseEntity::class,
        FlagEntity::class,
        MeetingEntity::class,
        MeetingAttendeeEntity::class,
        BriefingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MamYDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun noteDao(): NoteDao
    abstract fun actionDao(): ActionDao
    abstract fun promiseDao(): PromiseDao
    abstract fun flagDao(): FlagDao
    abstract fun meetingDao(): MeetingDao
    abstract fun meetingAttendeeDao(): MeetingAttendeeDao
    abstract fun briefingDao(): BriefingDao

    companion object {
        const val DB_NAME = "mamy.db"
    }
}
```

> **Note :** This file references entities/DAOs that don't exist yet — compilation will fail until Tasks 12-19 land them. We commit the skeleton + Converters now, and the database will compile cleanly at end of Task 19. The Converters test runs in isolation (no DB ref) and passes immediately.

- [ ] **Step 5 — Run Converters test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.converter.ConvertersTest" --no-daemon 2>&1 | tail -10`
Expected: 4 tests pass. Module compilation as a whole will fail because `MamYDatabase` references missing entities — that's expected and fixed in Tasks 12-19.

- [ ] **Step 6 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/data/db/MamYDatabase.kt app/src/main/kotlin/com/mamy/android/data/db/converter app/src/test/kotlin/com/mamy/android/data/db/converter
git commit -m "$(cat <<'EOF'
feat: add MamYDatabase skeleton + Instant/UUID type converters

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12 — `Person` entity + DAO + tests

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/PersonEntity.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/PersonDao.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/PersonDaoTest.kt`

**Steps**

- [ ] **Step 1 — Write failing in-memory Room test**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/PersonDaoTest.kt
package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PersonDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var dao: PersonDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.personDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `insert and getById round-trips`() = runTest {
        val p = samplePerson("Marie Dubois", "marie@example.com")
        dao.insert(p)
        val fetched = dao.getById(p.id)
        assertNotNull(fetched)
        assertEquals("Marie Dubois", fetched!!.name)
    }

    @Test
    fun `getByEmail returns matching person`() = runTest {
        dao.insert(samplePerson("Pierre Martin", "pierre@example.com"))
        val fetched = dao.getByEmail("pierre@example.com")
        assertNotNull(fetched)
        assertEquals("Pierre Martin", fetched!!.name)
    }

    @Test
    fun `getByEmail returns null for unknown email`() = runTest {
        assertNull(dao.getByEmail("nobody@x.com"))
    }

    @Test
    fun `getActiveOrderedByLastInteraction lists non-archived sorted desc`() = runTest {
        dao.insert(samplePerson("A", "a@x.com", lastInteraction = Instant.parse("2026-05-01T10:00:00Z")))
        dao.insert(samplePerson("B", "b@x.com", lastInteraction = Instant.parse("2026-05-02T10:00:00Z")))
        dao.insert(samplePerson("C", "c@x.com", archived = true))
        val list = dao.getActiveOrderedByLastInteraction()
        assertEquals(2, list.size)
        assertEquals("B", list[0].name)
        assertEquals("A", list[1].name)
    }

    @Test
    fun `update changes fields`() = runTest {
        val p = samplePerson("X", "x@x.com")
        dao.insert(p)
        dao.update(p.copy(roleHint = "Lead"))
        assertEquals("Lead", dao.getById(p.id)!!.roleHint)
    }

    @Test
    fun `deleteById removes row`() = runTest {
        val p = samplePerson("Y", "y@x.com")
        dao.insert(p)
        dao.deleteById(p.id)
        assertNull(dao.getById(p.id))
    }

    private fun samplePerson(
        name: String,
        email: String,
        lastInteraction: Instant? = null,
        archived: Boolean = false,
    ) = PersonEntity(
        id = UUID.randomUUID(),
        name = name,
        email = email,
        roleHint = null,
        calendarAttendeeId = email,
        createdAt = Instant.now(),
        lastInteractionAt = lastInteraction,
        interactionCount = 0,
        emotionalTrend = null,
        unmatched = true,
        archived = archived,
    )
}
```

- [ ] **Step 2 — Run test, expect FAIL (`Unresolved reference: PersonEntity`, `PersonDao`)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.PersonDaoTest" --no-daemon 2>&1 | tail -15`
Expected: compilation errors.

- [ ] **Step 3 — Create `PersonEntity.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/PersonEntity.kt
package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "person",
    indices = [
        Index(value = ["email"]),
        Index(value = ["calendar_attendee_id"]),
        Index(value = ["last_interaction_at"]),
    ],
)
data class PersonEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val email: String?,
    @ColumnInfo(name = "role_hint") val roleHint: String?,
    @ColumnInfo(name = "calendar_attendee_id") val calendarAttendeeId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "last_interaction_at") val lastInteractionAt: Instant?,
    @ColumnInfo(name = "interaction_count") val interactionCount: Int,
    @ColumnInfo(name = "emotional_trend") val emotionalTrend: String?,
    val unmatched: Boolean,
    val archived: Boolean,
)
```

- [ ] **Step 4 — Create `PersonDao.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/PersonDao.kt
package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PersonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: PersonEntity)

    @Update
    suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM person WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): PersonEntity?

    @Query("SELECT * FROM person WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): PersonEntity?

    @Query("SELECT * FROM person WHERE archived = 0 ORDER BY last_interaction_at DESC")
    suspend fun getActiveOrderedByLastInteraction(): List<PersonEntity>

    @Query("SELECT * FROM person WHERE archived = 0 ORDER BY last_interaction_at DESC")
    fun observeActive(): Flow<List<PersonEntity>>

    @Query("DELETE FROM person WHERE id = :id")
    suspend fun deleteById(id: UUID)
}
```

- [ ] **Step 5 — Run test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.PersonDaoTest" --no-daemon 2>&1 | tail -10`
Expected: 6 tests pass. The whole module still won't compile until other entities land — that's why we test only this DAO class for now.

- [ ] **Step 6 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/data/db/entity/PersonEntity.kt app/src/main/kotlin/com/mamy/android/data/db/dao/PersonDao.kt app/src/test/kotlin/com/mamy/android/data/db/dao/PersonDaoTest.kt
git commit -m "$(cat <<'EOF'
feat: add Person entity + DAO with insert/update/query/delete

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13 — `Note` entity + DAO + tests

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/NoteEntity.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/NoteDao.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/NoteDaoTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/NoteDaoTest.kt
package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NoteDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var noteDao: NoteDao
    private lateinit var personDao: PersonDao
    private lateinit var person: PersonEntity

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        noteDao = db.noteDao()
        personDao = db.personDao()
        person = PersonEntity(
            id = UUID.randomUUID(), name = "Marie", email = "m@x.com",
            roleHint = null, calendarAttendeeId = null, createdAt = Instant.now(),
            lastInteractionAt = null, interactionCount = 0, emotionalTrend = null,
            unmatched = false, archived = false,
        )
        personDao.insert(person)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `insert and getById round-trips`() = runTest {
        val n = sampleNote(person.id, "raw text")
        noteDao.insert(n)
        val fetched = noteDao.getById(n.id)
        assertNotNull(fetched)
        assertEquals("raw text", fetched!!.rawText)
    }

    @Test
    fun `getByPerson returns notes ordered desc`() = runTest {
        noteDao.insert(sampleNote(person.id, "old", Instant.parse("2026-05-01T08:00:00Z")))
        noteDao.insert(sampleNote(person.id, "new", Instant.parse("2026-05-02T08:00:00Z")))
        val list = noteDao.getByPersonOrderedDesc(person.id)
        assertEquals(2, list.size)
        assertEquals("new", list[0].rawText)
    }

    @Test
    fun `getNonStructuredNotes only returns flagged ones`() = runTest {
        noteDao.insert(sampleNote(person.id, "ok", nonStructured = false))
        noteDao.insert(sampleNote(person.id, "broken", nonStructured = true))
        val flagged = noteDao.getNonStructured()
        assertEquals(1, flagged.size)
        assertTrue(flagged[0].nonStructured)
    }

    private fun sampleNote(
        personId: UUID,
        rawText: String,
        createdAt: Instant = Instant.now(),
        nonStructured: Boolean = false,
    ) = NoteEntity(
        id = UUID.randomUUID(),
        personId = personId,
        meetingId = null,
        rawText = rawText,
        structuredJson = null,
        nonStructured = nonStructured,
        createdAt = createdAt,
        audioDurationSec = 30,
        llmProvider = "claude",
        llmCostCents = 1,
    )
}
```

- [ ] **Step 2 — Run test, expect FAIL**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.NoteDaoTest" --no-daemon 2>&1 | tail -10`
Expected: compilation error.

- [ ] **Step 3 — Create `NoteEntity.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/NoteEntity.kt
package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Note: `meeting_id` is stored as a plain UUID without an FK constraint.
 * Room enforces FKs at the database layer, but we deliberately skip that
 * here so this entity can be introduced in P1.13 *before* MeetingEntity
 * (P1.17) without breaking incremental task compilation. Application-level
 * integrity is enforced by [com.mamy.android.data.db.dao.NoteDao] callers.
 */
@Entity(
    tableName = "note",
    indices = [Index("person_id"), Index("meeting_id"), Index("created_at")],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "person_id") val personId: UUID?,
    @ColumnInfo(name = "meeting_id") val meetingId: UUID?,
    @ColumnInfo(name = "raw_text") val rawText: String,
    @ColumnInfo(name = "structured_json") val structuredJson: String?,
    @ColumnInfo(name = "non_structured") val nonStructured: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "audio_duration_sec") val audioDurationSec: Int,
    @ColumnInfo(name = "llm_provider") val llmProvider: String,
    @ColumnInfo(name = "llm_cost_cents") val llmCostCents: Int?,
)
```

- [ ] **Step 4 — Create `NoteDao.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/NoteDao.kt
package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamy.android.data.db.entity.NoteEntity
import java.util.UUID

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("SELECT * FROM note WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): NoteEntity?

    @Query("SELECT * FROM note WHERE person_id = :personId ORDER BY created_at DESC")
    suspend fun getByPersonOrderedDesc(personId: UUID): List<NoteEntity>

    @Query("SELECT * FROM note WHERE non_structured = 1 ORDER BY created_at DESC")
    suspend fun getNonStructured(): List<NoteEntity>

    @Query("DELETE FROM note WHERE id = :id")
    suspend fun deleteById(id: UUID)
}
```

- [ ] **Step 5 — Run test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.NoteDaoTest" --no-daemon 2>&1 | tail -10`
Expected: 3 tests pass. Module-wide compile still pending until Tasks 14-19.

- [ ] **Step 6 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/data/db/entity/NoteEntity.kt app/src/main/kotlin/com/mamy/android/data/db/dao/NoteDao.kt app/src/test/kotlin/com/mamy/android/data/db/dao/NoteDaoTest.kt
git commit -m "$(cat <<'EOF'
feat: add Note entity + DAO (transcripts + structured JSON)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 14 — `Action` entity + DAO + tests

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/ActionEntity.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/ActionDao.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/ActionDaoTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/ActionDaoTest.kt
package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActionDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var actionDao: ActionDao
    private lateinit var personId: UUID
    private lateinit var noteId: UUID

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        actionDao = db.actionDao()
        personId = UUID.randomUUID()
        noteId = UUID.randomUUID()
        db.personDao().insert(PersonEntity(
            id = personId, name = "Marie", email = "m@x.com", roleHint = null,
            calendarAttendeeId = null, createdAt = Instant.now(), lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null, unmatched = false, archived = false,
        ))
        db.noteDao().insert(NoteEntity(
            id = noteId, personId = personId, meetingId = null, rawText = "n",
            structuredJson = null, nonStructured = false, createdAt = Instant.now(),
            audioDurationSec = 0, llmProvider = "claude", llmCostCents = null,
        ))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `insert and getOpen lists only open actions`() = runTest {
        actionDao.insert(sampleAction("call David", "open"))
        actionDao.insert(sampleAction("done thing", "done"))
        val open = actionDao.getOpen()
        assertEquals(1, open.size)
        assertEquals("call David", open[0].description)
    }

    @Test
    fun `markDone sets status and done_at`() = runTest {
        val a = sampleAction("ping", "open")
        actionDao.insert(a)
        actionDao.markDone(a.id, Instant.parse("2026-05-02T20:00:00Z"))
        val updated = actionDao.getById(a.id)!!
        assertEquals("done", updated.status)
        assertTrue(updated.doneAt != null)
    }

    private fun sampleAction(desc: String, status: String) = ActionEntity(
        id = UUID.randomUUID(),
        description = desc,
        assignee = "self",
        linkedPersonId = personId,
        deadline = null,
        status = status,
        fromNoteId = noteId,
        createdAt = Instant.now(),
        doneAt = null,
    )
}
```

- [ ] **Step 2 — Run test, expect FAIL**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.ActionDaoTest" --no-daemon 2>&1 | tail -10`
Expected: compilation error.

- [ ] **Step 3 — Create `ActionEntity.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/ActionEntity.kt
package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "action",
    indices = [Index("linked_person_id"), Index("from_note_id"), Index("status"), Index("deadline")],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_person_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ActionEntity(
    @PrimaryKey val id: UUID,
    val description: String,
    val assignee: String,
    @ColumnInfo(name = "linked_person_id") val linkedPersonId: UUID?,
    val deadline: Instant?,
    val status: String,
    @ColumnInfo(name = "from_note_id") val fromNoteId: UUID,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "done_at") val doneAt: Instant?,
)
```

- [ ] **Step 4 — Create `ActionDao.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/ActionDao.kt
package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamy.android.data.db.entity.ActionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface ActionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: ActionEntity)

    @Update
    suspend fun update(action: ActionEntity)

    @Query("SELECT * FROM action WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): ActionEntity?

    @Query("SELECT * FROM action WHERE status = 'open' ORDER BY deadline ASC, created_at ASC")
    suspend fun getOpen(): List<ActionEntity>

    @Query("SELECT * FROM action WHERE status = 'open' ORDER BY deadline ASC, created_at ASC")
    fun observeOpen(): Flow<List<ActionEntity>>

    @Query("SELECT * FROM action WHERE linked_person_id = :personId ORDER BY created_at DESC")
    suspend fun getByPerson(personId: UUID): List<ActionEntity>

    @Query("UPDATE action SET status = 'done', done_at = :doneAt WHERE id = :id")
    suspend fun markDone(id: UUID, doneAt: Instant)

    @Query("DELETE FROM action WHERE id = :id")
    suspend fun deleteById(id: UUID)
}
```

- [ ] **Step 5 — Run test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.ActionDaoTest" --no-daemon 2>&1 | tail -10`
Expected: 2 tests pass.

- [ ] **Step 6 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/data/db/entity/ActionEntity.kt app/src/main/kotlin/com/mamy/android/data/db/dao/ActionDao.kt app/src/test/kotlin/com/mamy/android/data/db/dao/ActionDaoTest.kt
git commit -m "$(cat <<'EOF'
feat: add Action entity + DAO (open/done lifecycle)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 15 — `Promise` entity + DAO + tests

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/PromiseEntity.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/PromiseDao.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/PromiseDaoTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/PromiseDaoTest.kt
package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PromiseDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var promiseDao: PromiseDao
    private lateinit var noteId: UUID
    private val pierreId = UUID.randomUUID()

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        promiseDao = db.promiseDao()
        noteId = UUID.randomUUID()
        db.personDao().insert(PersonEntity(
            id = pierreId, name = "Pierre", email = null, roleHint = null,
            calendarAttendeeId = null, createdAt = Instant.now(), lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null, unmatched = false, archived = false,
        ))
        db.noteDao().insert(NoteEntity(
            id = noteId, personId = pierreId, meetingId = null, rawText = "n",
            structuredJson = null, nonStructured = false, createdAt = Instant.now(),
            audioDurationSec = 0, llmProvider = "claude", llmCostCents = null,
        ))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `getOwedToMe returns active promises FROM others TO self`() = runTest {
        promiseDao.insert(samplePromise(from = pierreId.toString(), to = "self", "mockup", "active"))
        promiseDao.insert(samplePromise(from = "self", to = pierreId.toString(), "feedback", "active"))
        promiseDao.insert(samplePromise(from = pierreId.toString(), to = "self", "old", "kept"))
        val owed = promiseDao.getOwedToMe()
        assertEquals(1, owed.size)
        assertEquals("mockup", owed[0].what)
    }

    @Test
    fun `getOwedByMe returns active promises FROM self TO others`() = runTest {
        promiseDao.insert(samplePromise(from = "self", to = pierreId.toString(), "feedback", "active"))
        promiseDao.insert(samplePromise(from = pierreId.toString(), to = "self", "mockup", "active"))
        val owed = promiseDao.getOwedByMe()
        assertEquals(1, owed.size)
        assertEquals("feedback", owed[0].what)
    }

    private fun samplePromise(from: String, to: String, what: String, status: String) = PromiseEntity(
        id = UUID.randomUUID(),
        fromId = from,
        toId = to,
        what = what,
        due = null,
        status = status,
        fromNoteId = noteId,
        createdAt = Instant.now(),
        resolvedAt = null,
    )
}
```

- [ ] **Step 2 — Run test, expect FAIL**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.PromiseDaoTest" --no-daemon 2>&1 | tail -10`
Expected: compilation error.

- [ ] **Step 3 — Create `PromiseEntity.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/PromiseEntity.kt
package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "promise",
    indices = [Index("from_id"), Index("to_id"), Index("status"), Index("due"), Index("from_note_id")],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PromiseEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "from_id") val fromId: String,
    @ColumnInfo(name = "to_id") val toId: String,
    val what: String,
    val due: Instant?,
    val status: String,
    @ColumnInfo(name = "from_note_id") val fromNoteId: UUID,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Instant?,
)
```

- [ ] **Step 4 — Create `PromiseDao.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/PromiseDao.kt
package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamy.android.data.db.entity.PromiseEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface PromiseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(promise: PromiseEntity)

    @Update
    suspend fun update(promise: PromiseEntity)

    @Query("SELECT * FROM promise WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): PromiseEntity?

    @Query("SELECT * FROM promise WHERE to_id = 'self' AND status = 'active' ORDER BY due ASC, created_at ASC")
    suspend fun getOwedToMe(): List<PromiseEntity>

    @Query("SELECT * FROM promise WHERE from_id = 'self' AND status = 'active' ORDER BY due ASC, created_at ASC")
    suspend fun getOwedByMe(): List<PromiseEntity>

    @Query("SELECT * FROM promise WHERE (from_id = :personIdStr OR to_id = :personIdStr) ORDER BY created_at DESC")
    suspend fun getByPerson(personIdStr: String): List<PromiseEntity>

    @Query("SELECT * FROM promise WHERE status = 'active'")
    fun observeActive(): Flow<List<PromiseEntity>>

    @Query("UPDATE promise SET status = :status, resolved_at = :resolvedAt WHERE id = :id")
    suspend fun resolve(id: UUID, status: String, resolvedAt: Instant)

    @Query("DELETE FROM promise WHERE id = :id")
    suspend fun deleteById(id: UUID)
}
```

- [ ] **Step 5 — Run test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.PromiseDaoTest" --no-daemon 2>&1 | tail -10`
Expected: 2 tests pass.

- [ ] **Step 6 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/data/db/entity/PromiseEntity.kt app/src/main/kotlin/com/mamy/android/data/db/dao/PromiseDao.kt app/src/test/kotlin/com/mamy/android/data/db/dao/PromiseDaoTest.kt
git commit -m "$(cat <<'EOF'
feat: add Promise entity + DAO (bilateral promise tracking)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 16 — `Flag` entity + DAO + tests

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/FlagEntity.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/FlagDao.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/FlagDaoTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/FlagDaoTest.kt
package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FlagDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var flagDao: FlagDao
    private val pierreId = UUID.randomUUID()
    private lateinit var noteId: UUID

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        flagDao = db.flagDao()
        noteId = UUID.randomUUID()
        db.personDao().insert(PersonEntity(
            id = pierreId, name = "Pierre", email = null, roleHint = null,
            calendarAttendeeId = null, createdAt = Instant.now(), lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null, unmatched = false, archived = false,
        ))
        db.noteDao().insert(NoteEntity(
            id = noteId, personId = pierreId, meetingId = null, rawText = "n",
            structuredJson = null, nonStructured = false, createdAt = Instant.now(),
            audioDurationSec = 0, llmProvider = "claude", llmCostCents = null,
        ))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `getOpenByPerson returns unresolved flags`() = runTest {
        flagDao.insert(sampleFlag("demotivation", false))
        flagDao.insert(sampleFlag("burnout", true))
        val open = flagDao.getOpenByPerson(pierreId)
        assertEquals(1, open.size)
        assertEquals("demotivation", open[0].type)
    }

    @Test
    fun `markResolved sets resolved=true`() = runTest {
        val f = sampleFlag("conflict", false)
        flagDao.insert(f)
        flagDao.markResolved(f.id)
        assertTrue(flagDao.getById(f.id)!!.resolved)
    }

    private fun sampleFlag(type: String, resolved: Boolean) = FlagEntity(
        id = UUID.randomUUID(),
        personId = pierreId,
        type = type,
        source = "indirect:Marie",
        severity = "medium",
        note = "via Marie",
        resolved = resolved,
        fromNoteId = noteId,
        createdAt = Instant.now(),
    )
}
```

- [ ] **Step 2 — Run test, expect FAIL**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.FlagDaoTest" --no-daemon 2>&1 | tail -10`
Expected: compilation error.

- [ ] **Step 3 — Create `FlagEntity.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/FlagEntity.kt
package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "flag",
    indices = [Index("person_id"), Index("type"), Index("resolved"), Index("from_note_id")],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FlagEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "person_id") val personId: UUID,
    val type: String,
    val source: String,
    val severity: String,
    val note: String,
    val resolved: Boolean,
    @ColumnInfo(name = "from_note_id") val fromNoteId: UUID,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)
```

- [ ] **Step 4 — Create `FlagDao.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/FlagDao.kt
package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamy.android.data.db.entity.FlagEntity
import java.util.UUID

@Dao
interface FlagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flag: FlagEntity)

    @Update
    suspend fun update(flag: FlagEntity)

    @Query("SELECT * FROM flag WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): FlagEntity?

    @Query("SELECT * FROM flag WHERE person_id = :personId AND resolved = 0 ORDER BY severity DESC, created_at DESC")
    suspend fun getOpenByPerson(personId: UUID): List<FlagEntity>

    @Query("SELECT * FROM flag WHERE resolved = 0 ORDER BY severity DESC, created_at DESC")
    suspend fun getAllOpen(): List<FlagEntity>

    @Query("UPDATE flag SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: UUID)

    @Query("DELETE FROM flag WHERE id = :id")
    suspend fun deleteById(id: UUID)
}
```

- [ ] **Step 5 — Run test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.FlagDaoTest" --no-daemon 2>&1 | tail -10`
Expected: 2 tests pass.

- [ ] **Step 6 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/data/db/entity/FlagEntity.kt app/src/main/kotlin/com/mamy/android/data/db/dao/FlagDao.kt app/src/test/kotlin/com/mamy/android/data/db/dao/FlagDaoTest.kt
git commit -m "$(cat <<'EOF'
feat: add Flag entity + DAO (demotivation/conflict/risk markers)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 17 — `Meeting` entity + DAO + tests

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/MeetingEntity.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/MeetingDao.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/MeetingDaoTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/MeetingDaoTest.kt
package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.MeetingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MeetingDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var dao: MeetingDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.meetingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `getInRange returns meetings whose start is between bounds`() = runTest {
        dao.insert(sampleMeeting("morning", Instant.parse("2026-05-02T09:00:00Z")))
        dao.insert(sampleMeeting("afternoon", Instant.parse("2026-05-02T14:00:00Z")))
        dao.insert(sampleMeeting("tomorrow", Instant.parse("2026-05-03T09:00:00Z")))
        val today = dao.getInRange(
            Instant.parse("2026-05-02T00:00:00Z"),
            Instant.parse("2026-05-02T23:59:59Z"),
        )
        assertEquals(2, today.size)
    }

    @Test
    fun `getByCalendarEventId returns matching event`() = runTest {
        dao.insert(sampleMeeting("ev", Instant.now(), calendarEventId = "ev-123"))
        val fetched = dao.getByCalendarEventId("ev-123")
        assertEquals("ev", fetched!!.title)
    }

    private fun sampleMeeting(title: String, startsAt: Instant, calendarEventId: String? = null) =
        MeetingEntity(
            id = UUID.randomUUID(),
            calendarEventId = calendarEventId,
            title = title,
            startsAt = startsAt,
            endsAt = startsAt.plusSeconds(1800),
            briefingText = null,
            postNoteId = null,
            createdAt = Instant.now(),
        )
}
```

- [ ] **Step 2 — Run test, expect FAIL**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.MeetingDaoTest" --no-daemon 2>&1 | tail -10`
Expected: compilation error.

- [ ] **Step 3 — Create `MeetingEntity.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/MeetingEntity.kt
package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "meeting",
    indices = [
        Index(value = ["calendar_event_id"], unique = true),
        Index("starts_at"),
        Index("post_note_id"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["post_note_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class MeetingEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "calendar_event_id") val calendarEventId: String?,
    val title: String,
    @ColumnInfo(name = "starts_at") val startsAt: Instant,
    @ColumnInfo(name = "ends_at") val endsAt: Instant,
    @ColumnInfo(name = "briefing_text") val briefingText: String?,
    @ColumnInfo(name = "post_note_id") val postNoteId: UUID?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)
```

- [ ] **Step 4 — Create `MeetingDao.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/MeetingDao.kt
package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamy.android.data.db.entity.MeetingEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface MeetingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meeting: MeetingEntity)

    @Update
    suspend fun update(meeting: MeetingEntity)

    @Query("SELECT * FROM meeting WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): MeetingEntity?

    @Query("SELECT * FROM meeting WHERE calendar_event_id = :eventId LIMIT 1")
    suspend fun getByCalendarEventId(eventId: String): MeetingEntity?

    @Query("SELECT * FROM meeting WHERE starts_at BETWEEN :from AND :to ORDER BY starts_at ASC")
    suspend fun getInRange(from: Instant, to: Instant): List<MeetingEntity>

    @Query("SELECT * FROM meeting WHERE starts_at BETWEEN :from AND :to ORDER BY starts_at ASC")
    fun observeInRange(from: Instant, to: Instant): Flow<List<MeetingEntity>>

    @Query("DELETE FROM meeting WHERE id = :id")
    suspend fun deleteById(id: UUID)
}
```

- [ ] **Step 5 — Run test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.MeetingDaoTest" --no-daemon 2>&1 | tail -10`
Expected: 2 tests pass.

- [ ] **Step 6 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/data/db/entity/MeetingEntity.kt app/src/main/kotlin/com/mamy/android/data/db/dao/MeetingDao.kt app/src/test/kotlin/com/mamy/android/data/db/dao/MeetingDaoTest.kt
git commit -m "$(cat <<'EOF'
feat: add Meeting entity + DAO (calendar event mirror)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 18 — `MeetingAttendee` entity + DAO + tests

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/MeetingAttendeeEntity.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/MeetingAttendeeDao.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/MeetingAttendeeDaoTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/MeetingAttendeeDaoTest.kt
package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.MeetingAttendeeEntity
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MeetingAttendeeDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var attendeeDao: MeetingAttendeeDao
    private lateinit var meetingId: UUID
    private lateinit var personA: UUID
    private lateinit var personB: UUID

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        attendeeDao = db.meetingAttendeeDao()
        meetingId = UUID.randomUUID()
        personA = UUID.randomUUID()
        personB = UUID.randomUUID()
        db.meetingDao().insert(MeetingEntity(
            id = meetingId, calendarEventId = null, title = "1:1",
            startsAt = Instant.now(), endsAt = Instant.now().plusSeconds(1800),
            briefingText = null, postNoteId = null, createdAt = Instant.now(),
        ))
        db.personDao().insert(PersonEntity(
            id = personA, name = "A", email = null, roleHint = null,
            calendarAttendeeId = null, createdAt = Instant.now(), lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null, unmatched = false, archived = false,
        ))
        db.personDao().insert(PersonEntity(
            id = personB, name = "B", email = null, roleHint = null,
            calendarAttendeeId = null, createdAt = Instant.now(), lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null, unmatched = false, archived = false,
        ))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `insert and getPersonsForMeeting returns linked persons`() = runTest {
        attendeeDao.insert(MeetingAttendeeEntity(meetingId, personA))
        attendeeDao.insert(MeetingAttendeeEntity(meetingId, personB))
        val ids = attendeeDao.getPersonIdsForMeeting(meetingId)
        assertEquals(2, ids.size)
    }

    @Test
    fun `getMeetingsForPerson returns all meetings`() = runTest {
        attendeeDao.insert(MeetingAttendeeEntity(meetingId, personA))
        val meetings = attendeeDao.getMeetingIdsForPerson(personA)
        assertEquals(1, meetings.size)
    }
}
```

- [ ] **Step 2 — Run test, expect FAIL**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.MeetingAttendeeDaoTest" --no-daemon 2>&1 | tail -10`
Expected: compilation error.

- [ ] **Step 3 — Create `MeetingAttendeeEntity.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/MeetingAttendeeEntity.kt
package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "meeting_attendee",
    primaryKeys = ["meeting_id", "person_id"],
    indices = [Index("person_id")],
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meeting_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MeetingAttendeeEntity(
    @ColumnInfo(name = "meeting_id") val meetingId: UUID,
    @ColumnInfo(name = "person_id") val personId: UUID,
)
```

- [ ] **Step 4 — Create `MeetingAttendeeDao.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/MeetingAttendeeDao.kt
package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mamy.android.data.db.entity.MeetingAttendeeEntity
import java.util.UUID

@Dao
interface MeetingAttendeeDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(attendee: MeetingAttendeeEntity)

    @Query("SELECT person_id FROM meeting_attendee WHERE meeting_id = :meetingId")
    suspend fun getPersonIdsForMeeting(meetingId: UUID): List<UUID>

    @Query("SELECT meeting_id FROM meeting_attendee WHERE person_id = :personId")
    suspend fun getMeetingIdsForPerson(personId: UUID): List<UUID>

    @Query("DELETE FROM meeting_attendee WHERE meeting_id = :meetingId AND person_id = :personId")
    suspend fun delete(meetingId: UUID, personId: UUID)

    @Query("DELETE FROM meeting_attendee WHERE meeting_id = :meetingId")
    suspend fun deleteAllForMeeting(meetingId: UUID)
}
```

- [ ] **Step 5 — Run test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.MeetingAttendeeDaoTest" --no-daemon 2>&1 | tail -10`
Expected: 2 tests pass.

- [ ] **Step 6 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/data/db/entity/MeetingAttendeeEntity.kt app/src/main/kotlin/com/mamy/android/data/db/dao/MeetingAttendeeDao.kt app/src/test/kotlin/com/mamy/android/data/db/dao/MeetingAttendeeDaoTest.kt
git commit -m "$(cat <<'EOF'
feat: add MeetingAttendee join entity + DAO

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 19 — `Briefing` entity + DAO + tests (closes the database)

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/BriefingEntity.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/BriefingDao.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/BriefingDaoTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/db/dao/BriefingDaoTest.kt
package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.BriefingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BriefingDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var dao: BriefingDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.briefingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `getValidByTypeAndTarget returns non-expired briefing`() = runTest {
        val now = Instant.parse("2026-05-02T08:00:00Z")
        dao.insert(sampleBriefing(
            type = "daily",
            targetId = null,
            generatedAt = now,
            expiresAt = now.plusSeconds(8 * 3600),
        ))
        val fetched = dao.getValidByTypeAndTarget("daily", null, now.plusSeconds(60))
        assertEquals("daily", fetched!!.type)
    }

    @Test
    fun `getValidByTypeAndTarget returns null for expired`() = runTest {
        val now = Instant.parse("2026-05-02T08:00:00Z")
        dao.insert(sampleBriefing(
            type = "daily",
            targetId = null,
            generatedAt = now.minusSeconds(10 * 3600),
            expiresAt = now.minusSeconds(2 * 3600),
        ))
        assertNull(dao.getValidByTypeAndTarget("daily", null, now))
    }

    @Test
    fun `deleteExpired removes only expired entries`() = runTest {
        val now = Instant.parse("2026-05-02T08:00:00Z")
        dao.insert(sampleBriefing("a", null, now, now.plusSeconds(3600)))
        dao.insert(sampleBriefing("b", null, now.minusSeconds(7200), now.minusSeconds(3600)))
        dao.deleteExpired(now)
        assertEquals(1, dao.countAll())
    }

    private fun sampleBriefing(
        type: String,
        targetId: String?,
        generatedAt: Instant,
        expiresAt: Instant,
    ) = BriefingEntity(
        id = UUID.randomUUID(),
        type = type,
        targetId = targetId,
        generatedAt = generatedAt,
        expiresAt = expiresAt,
        text = "briefing text",
        llmProvider = "claude",
        llmCostCents = 2,
    )
}
```

- [ ] **Step 2 — Run test, expect FAIL**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.BriefingDaoTest" --no-daemon 2>&1 | tail -10`
Expected: compilation error.

- [ ] **Step 3 — Create `BriefingEntity.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/entity/BriefingEntity.kt
package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "briefing",
    indices = [Index("type"), Index("target_id"), Index("expires_at")],
)
data class BriefingEntity(
    @PrimaryKey val id: UUID,
    val type: String,
    @ColumnInfo(name = "target_id") val targetId: String?,
    @ColumnInfo(name = "generated_at") val generatedAt: Instant,
    @ColumnInfo(name = "expires_at") val expiresAt: Instant,
    val text: String,
    @ColumnInfo(name = "llm_provider") val llmProvider: String,
    @ColumnInfo(name = "llm_cost_cents") val llmCostCents: Int?,
)
```

- [ ] **Step 4 — Create `BriefingDao.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/db/dao/BriefingDao.kt
package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mamy.android.data.db.entity.BriefingEntity
import java.time.Instant

@Dao
interface BriefingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(briefing: BriefingEntity)

    @Query(
        "SELECT * FROM briefing WHERE type = :type AND ((:targetId IS NULL AND target_id IS NULL) OR target_id = :targetId) " +
            "AND expires_at > :now ORDER BY generated_at DESC LIMIT 1"
    )
    suspend fun getValidByTypeAndTarget(type: String, targetId: String?, now: Instant): BriefingEntity?

    @Query("DELETE FROM briefing WHERE expires_at <= :now")
    suspend fun deleteExpired(now: Instant)

    @Query("SELECT COUNT(*) FROM briefing")
    suspend fun countAll(): Int
}
```

- [ ] **Step 5 — Run full module compile + this test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.mamy.android.data.db.dao.BriefingDaoTest" --no-daemon 2>&1 | tail -15`
Expected: `BUILD SUCCESSFUL`. The whole `MamYDatabase` now compiles end-to-end (all 8 entities + 8 DAOs wired). 3 tests pass.

- [ ] **Step 6 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/data/db/entity/BriefingEntity.kt app/src/main/kotlin/com/mamy/android/data/db/dao/BriefingDao.kt app/src/test/kotlin/com/mamy/android/data/db/dao/BriefingDaoTest.kt
git commit -m "$(cat <<'EOF'
feat: add Briefing entity + DAO (cache TTL + close 8-table schema)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 20 — `SettingsRepository` (DataStore preferences)

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/settings/SettingsRepository.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/settings/SettingsRepositoryTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test for language + briefing schedule round-trip**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/settings/SettingsRepositoryTest.kt
package com.mamy.android.data.settings

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SettingsRepositoryTest {

    @TempDir lateinit var tempDir: Path
    private lateinit var repo: SettingsRepository
    private lateinit var dataStore: androidx.datastore.core.DataStore<Preferences>

    @BeforeEach
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir.toFile(), "settings.preferences_pb") },
        )
        repo = SettingsRepository(dataStore)
    }

    @AfterEach
    fun tearDown() {
        // tempDir cleaned up by JUnit
    }

    @Test
    fun `default language is system`() = runTest {
        repo.languageFlow.test {
            assertEquals(SettingsRepository.Language.SYSTEM, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setLanguage persists and emits new value`() = runTest {
        repo.setLanguage(SettingsRepository.Language.FR)
        repo.languageFlow.test {
            assertEquals(SettingsRepository.Language.FR, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default briefing time is 8h00`() = runTest {
        repo.dailyBriefingHourFlow.test {
            assertEquals(8, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDailyBriefingHour persists`() = runTest {
        repo.setDailyBriefingHour(7)
        repo.dailyBriefingHourFlow.test {
            assertEquals(7, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default selected llm provider is claude`() = runTest {
        repo.selectedLlmProviderFlow.test {
            assertEquals(SettingsRepository.LlmProvider.CLAUDE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2 — Run test, expect FAIL**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.settings.SettingsRepositoryTest" --no-daemon 2>&1 | tail -10`
Expected: compilation error.

- [ ] **Step 3 — Create `SettingsRepository.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/settings/SettingsRepository.kt
package com.mamy.android.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stores non-sensitive user preferences in DataStore. Sensitive values (BYOK API keys,
 * OAuth tokens) live in [com.mamy.android.data.secrets.SecretsVault] under keystore-wrapped AES.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    enum class Language { SYSTEM, EN, FR }
    enum class LlmProvider { CLAUDE, OPENAI, GEMINI }
    enum class PrivacyMode { STANDARD, STRICT, HYBRID_REDACTION }

    val languageFlow: Flow<Language> = dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE]?.let(::safeLanguage) ?: Language.SYSTEM
    }

    val dailyBriefingHourFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_DAILY_BRIEFING_HOUR] ?: DEFAULT_BRIEFING_HOUR
    }

    val selectedLlmProviderFlow: Flow<LlmProvider> = dataStore.data.map { prefs ->
        prefs[KEY_LLM_PROVIDER]?.let(::safeProvider) ?: LlmProvider.CLAUDE
    }

    val privacyModeFlow: Flow<PrivacyMode> = dataStore.data.map { prefs ->
        prefs[KEY_PRIVACY_MODE]?.let(::safePrivacy) ?: PrivacyMode.STANDARD
    }

    val wakeWordSensitivityFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_WAKEWORD_SENSITIVITY] ?: DEFAULT_WAKEWORD_SENSITIVITY
    }

    suspend fun setLanguage(value: Language) {
        dataStore.edit { it[KEY_LANGUAGE] = value.name }
    }

    suspend fun setDailyBriefingHour(hour: Int) {
        require(hour in 0..23) { "hour must be 0..23" }
        dataStore.edit { it[KEY_DAILY_BRIEFING_HOUR] = hour }
    }

    suspend fun setSelectedLlmProvider(value: LlmProvider) {
        dataStore.edit { it[KEY_LLM_PROVIDER] = value.name }
    }

    suspend fun setPrivacyMode(value: PrivacyMode) {
        dataStore.edit { it[KEY_PRIVACY_MODE] = value.name }
    }

    suspend fun setWakeWordSensitivity(level: Int) {
        require(level in 0..2) { "level must be 0..2 (low|medium|high)" }
        dataStore.edit { it[KEY_WAKEWORD_SENSITIVITY] = level }
    }

    private fun safeLanguage(raw: String): Language =
        runCatching { Language.valueOf(raw) }.getOrDefault(Language.SYSTEM)

    private fun safeProvider(raw: String): LlmProvider =
        runCatching { LlmProvider.valueOf(raw) }.getOrDefault(LlmProvider.CLAUDE)

    private fun safePrivacy(raw: String): PrivacyMode =
        runCatching { PrivacyMode.valueOf(raw) }.getOrDefault(PrivacyMode.STANDARD)

    companion object {
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_DAILY_BRIEFING_HOUR = intPreferencesKey("daily_briefing_hour")
        private val KEY_LLM_PROVIDER = stringPreferencesKey("llm_provider")
        private val KEY_PRIVACY_MODE = stringPreferencesKey("privacy_mode")
        private val KEY_WAKEWORD_SENSITIVITY = intPreferencesKey("wakeword_sensitivity")

        const val DEFAULT_BRIEFING_HOUR = 8
        const val DEFAULT_WAKEWORD_SENSITIVITY = 1
    }
}
```

- [ ] **Step 4 — Run test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.data.settings.SettingsRepositoryTest" --no-daemon 2>&1 | tail -10`
Expected: 5 tests pass.

- [ ] **Step 5 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/data/settings/SettingsRepository.kt app/src/test/kotlin/com/mamy/android/data/settings/SettingsRepositoryTest.kt
git commit -m "$(cat <<'EOF'
feat: add SettingsRepository (DataStore prefs for lang/briefing/LLM/privacy)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 21 — Hilt DI modules + `MainActivity` + `MamYListenerService` skeleton + smoke

**Files**
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/di/DatabaseModule.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/di/SecretsModule.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/di/SettingsModule.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/MainActivity.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/service/MamYListenerService.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/androidTest/kotlin/com/mamy/android/MamYTestRunner.kt`
- Create: `D:/ComfyUI-Intel/mamy/app/src/main/res/drawable/ic_mamy_listener.xml`
- Create: `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/di/DiModulesTest.kt`

**Steps**

- [ ] **Step 1 — Write failing test that asserts the three DI module classes exist with `@Module` + `@InstallIn`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/di/DiModulesTest.kt
package com.mamy.android.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DiModulesTest {

    @Test
    fun `DatabaseModule is annotated @Module @InstallIn(SingletonComponent)`() {
        assertNotNull(DatabaseModule::class.java.getAnnotation(Module::class.java))
        val install = DatabaseModule::class.java.getAnnotation(InstallIn::class.java)
        assertNotNull(install)
        assertEquals(SingletonComponent::class.java, install!!.value[0].java)
    }

    @Test
    fun `SecretsModule is annotated @Module @InstallIn(SingletonComponent)`() {
        assertNotNull(SecretsModule::class.java.getAnnotation(Module::class.java))
        val install = SecretsModule::class.java.getAnnotation(InstallIn::class.java)
        assertNotNull(install)
        assertEquals(SingletonComponent::class.java, install!!.value[0].java)
    }

    @Test
    fun `SettingsModule is annotated @Module @InstallIn(SingletonComponent)`() {
        assertNotNull(SettingsModule::class.java.getAnnotation(Module::class.java))
        val install = SettingsModule::class.java.getAnnotation(InstallIn::class.java)
        assertNotNull(install)
        assertEquals(SingletonComponent::class.java, install!!.value[0].java)
    }
}
```

- [ ] **Step 2 — Run test, expect FAIL (`Unresolved reference: DatabaseModule`...)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.di.DiModulesTest" --no-daemon 2>&1 | tail -10`
Expected: compilation error.

- [ ] **Step 3 — Create `DatabaseModule.kt` (SQLCipher-wrapped Room build)**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/di/DatabaseModule.kt
package com.mamy.android.di

import android.content.Context
import androidx.room.Room
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.BriefingDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.secrets.SecretsVault
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        secretsVault: SecretsVault,
    ): MamYDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = secretsVault.getOrCreateDbPassphrase()
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, MamYDatabase::class.java, MamYDatabase.DB_NAME)
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun providePersonDao(db: MamYDatabase): PersonDao = db.personDao()
    @Provides fun provideNoteDao(db: MamYDatabase): NoteDao = db.noteDao()
    @Provides fun provideActionDao(db: MamYDatabase): ActionDao = db.actionDao()
    @Provides fun providePromiseDao(db: MamYDatabase): PromiseDao = db.promiseDao()
    @Provides fun provideFlagDao(db: MamYDatabase): FlagDao = db.flagDao()
    @Provides fun provideMeetingDao(db: MamYDatabase): MeetingDao = db.meetingDao()
    @Provides fun provideMeetingAttendeeDao(db: MamYDatabase): MeetingAttendeeDao = db.meetingAttendeeDao()
    @Provides fun provideBriefingDao(db: MamYDatabase): BriefingDao = db.briefingDao()
}
```

- [ ] **Step 4 — Create `SecretsModule.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/di/SecretsModule.kt
package com.mamy.android.di

import android.content.Context
import com.mamy.android.data.secrets.KeystoreHelper
import com.mamy.android.data.secrets.SecretsVault
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecretsModule {

    @Provides
    @Singleton
    fun provideKeystoreHelper(): KeystoreHelper = KeystoreHelper()

    @Provides
    @Singleton
    fun provideSecretsVault(
        @ApplicationContext context: Context,
        keystoreHelper: KeystoreHelper,
    ): SecretsVault = SecretsVault(context, keystoreHelper)
}
```

- [ ] **Step 5 — Create `SettingsModule.kt`**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/di/SettingsModule.kt
package com.mamy.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mamy.android.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "mamy_settings")

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<Preferences>): SettingsRepository =
        SettingsRepository(dataStore)
}
```

- [ ] **Step 6 — Create `MainActivity.kt` (Compose entry, @AndroidEntryPoint)**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/MainActivity.kt
package com.mamy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mamy.android.ui.nav.MamYNav
import com.mamy.android.ui.theme.MamYTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MamYApp() }
    }
}

@Composable
fun MamYApp() {
    MamYTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
                MamYNav()
            }
        }
    }
}
```

- [ ] **Step 7 — Create `MamYListenerService.kt` skeleton (foreground notif only, NO audio capture; P2 wires that)**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/service/MamYListenerService.kt
package com.mamy.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.mamy.android.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service that will host wake-word + audio pipeline starting in P2.
 * In P1 this skeleton only:
 *  - declares a notification channel
 *  - posts the permanent notification
 *  - starts in foreground with the microphone foregroundServiceType
 *
 * Audio capture, Porcupine, Whisper are wired in P2.
 */
@AndroidEntryPoint
class MamYListenerService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundCompat()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.service_notif_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat() {
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notif_title))
            .setContentText(getString(R.string.service_notif_text))
            .setSmallIcon(R.drawable.ic_mamy_listener)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    companion object {
        const val CHANNEL_ID = "mamy_listener"
        const val NOTIF_ID = 6291
    }
}
```

- [ ] **Step 8 — Create `ic_mamy_listener.xml` icon resource**

```xml
<!-- D:/ComfyUI-Intel/mamy/app/src/main/res/drawable/ic_mamy_listener.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z" />
</vector>
```

- [ ] **Step 9 — Create `MamYTestRunner.kt` (Hilt + AndroidJUnitRunner bridge for instrumented tests)**

```kotlin
// D:/ComfyUI-Intel/mamy/app/src/androidTest/kotlin/com/mamy/android/MamYTestRunner.kt
package com.mamy.android

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class MamYTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
```

- [ ] **Step 10 — Run unit DI test, expect PASS**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --tests "com.mamy.android.di.DiModulesTest" --no-daemon 2>&1 | tail -10`
Expected: 3 tests pass.

- [ ] **Step 11 — Build full debug APK as smoke check (`./gradlew :app:assembleDebug`)**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:assembleDebug --no-daemon 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`. APK appears at `app/build/outputs/apk/debug/app-debug.apk`. Hilt processor runs cleanly; manifest merges; resources resolve. If `BuildConfig.SQLCIPHER` errors appear, add `import net.zetetic.database.sqlcipher.SupportOpenHelperFactory` (already present).

- [ ] **Step 12 — Run all unit tests as final P1 smoke**

Run: `cd D:/ComfyUI-Intel/mamy && gradle.bat :app:testDebugUnitTest --no-daemon 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL` + every P1 test passes (KeystoreHelper 2 + SecretsVault 6 + Converters 4 + 8 DAO test classes = 24 + Settings 5 + Routes 2 + DI 3 + MamYApplication 2 = ~48 tests).

- [ ] **Step 13 — Commit**

```bash
git add app/src/main/kotlin/com/mamy/android/di app/src/main/kotlin/com/mamy/android/MainActivity.kt app/src/main/kotlin/com/mamy/android/service/MamYListenerService.kt app/src/main/res/drawable/ic_mamy_listener.xml app/src/androidTest/kotlin/com/mamy/android/MamYTestRunner.kt app/src/test/kotlin/com/mamy/android/di/DiModulesTest.kt
git commit -m "$(cat <<'EOF'
feat: wire Hilt DI modules + MainActivity + foreground service skeleton

Closes P1 foundation: DB/Secrets/Settings injected app-wide, MainActivity
hosts the Compose nav scaffold, MamYListenerService stands up a foreground
notification channel with microphone foregroundServiceType ready for P2
to plug Porcupine + AudioCapture + Whisper.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## P1 closure checklist

After Task 21:
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] `./gradlew :app:testDebugUnitTest` reports ≥ 48 tests, all green
- [ ] Manifest declares all 7 permissions + foreground service with microphone type
- [ ] `MamYDatabase` builds with all 8 entities + 8 DAOs registered
- [ ] `SecretsVault` round-trips strings and 32-byte DB passphrase
- [ ] `SettingsRepository` exposes Flow defaults + setters for 5 prefs
- [ ] FR + EN strings.xml cover every visible string in P1
- [ ] Hilt graph resolves at app start (no `MissingBinding` errors during smoke)

P2 (voice capture) plugs into:
- `MamYListenerService.startForegroundCompat()` → add Porcupine + AudioCapture + Whisper engines
- `SettingsRepository.wakeWordSensitivityFlow` → drive Porcupine sensitivity at runtime
- `SecretsVault.getSecret("claude_api_key")` → BYOK call from `LlmStructurer`

---

## Notes on parallel coordination

This plan creates **only** the files and packages it explicitly lists. P2-P8 plans should:
- **Reuse**: every entity/DAO, `MamYDatabase`, `SecretsVault`, `KeystoreHelper`, `SettingsRepository`, `MamYTheme`, `Routes`, `MamYNav`, `MamYListenerService`
- **Extend**: add new packages under `data/`, `domain/`, `service/`, `ui/screens/` — never modify P1 sources except via TDD-backed PRs
- **Add**: their own DI modules under `com.mamy.android.di.<area>Module.kt`
- **Replace placeholders**: `OnboardingScreen`, `ReportsListScreen`, etc., are stubs; P7 swaps them with real Composables behind the same route names
