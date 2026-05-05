# MamY Release Pipeline

How to ship a new MamY APK / AAB to alpha testers via GitHub Releases.

## Prerequisites (one-time)

1. **Generate the release keystore** (if not already done):

   ```bash
   cd D:/mamy
   keytool -genkeypair -v \
     -keystore keystore/mamy-release.jks \
     -alias mamy \
     -keyalg RSA -keysize 2048 -validity 9125 \
     -dname "CN=MamY,OU=Engineering,O=MamY,L=Paris,ST=IDF,C=FR"
   ```

   Store the password in your password manager.

2. **Create `signing.properties`** at the repo root (gitignored):

   ```properties
   storeFile=keystore/mamy-release.jks
   storePassword=<store password>
   keyAlias=mamy
   keyPassword=<key password>
   ```

3. **Configure GitHub repo secrets** (Settings > Secrets and variables > Actions):

   | Secret | Value |
   | --- | --- |
   | `KEYSTORE_BASE64` | Output of `base64 -w 0 keystore/mamy-release.jks` |
   | `KEYSTORE_PASSWORD` | Store password from step 1 |
   | `KEY_ALIAS` | `mamy` |
   | `KEY_PASSWORD` | Key password from step 1 |

   On macOS use `base64 -i keystore/mamy-release.jks | tr -d '\n'`.
   On Windows PowerShell:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore/mamy-release.jks"))
   ```

## Local build (smoke test before tagging)

```bash
export ANDROID_HOME="C:/Users/sxc_2/AppData/Local/Android/Sdk"
cd D:/mamy

# Verify both build targets
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:assembleRelease --no-daemon
./gradlew :app:bundleRelease --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon

# Verify signing
"$ANDROID_HOME/build-tools/34.0.0/apksigner.bat" verify --verbose \
  app/build/outputs/apk/release/app-release.apk
```

Expected signing output: `Verified using v3 scheme: true` (with minSdk 28,
v3 alone is sufficient; v1+v2 are skipped by AGP).

## Publish a release (push a tag)

```bash
cd D:/mamy

# Bump versionCode + versionName in app/build.gradle.kts first
# Then commit + tag

git add app/build.gradle.kts
git commit -m "chore(release): bump to v0.1.1"

git tag v0.1.1
git push origin main
git push origin v0.1.1
```

The `release-apk.yml` workflow runs on the tag push:

1. Decodes the keystore from `KEYSTORE_BASE64` secret
2. Writes `signing.properties` from secrets
3. Builds signed APK + AAB
4. Verifies signing with apksigner
5. Creates a GitHub Release with auto-generated notes
6. Attaches `app-release.apk` + `app-release.aab` as release assets

Watch progress at: `https://github.com/<org>/mamy/actions`

## Tester onboarding

Send testers the GitHub Release URL. They:

1. Open the release page on their Android device.
2. Tap `app-release.apk` to download.
3. Follow Android's "Install unknown app" prompt (one-time per browser/file
   manager).
4. Open MamY, complete the 7-step onboarding.
5. Drop their `mamy_en.ppn` + `mamy_fr.ppn` into Android `Documents/MamY/wakeword/`
   (or follow the in-app prompt).

## Rollback

To pull a release:

1. Mark the GitHub Release as a draft (Settings > Releases > Edit).
2. Delete the tag locally + remotely:
   ```bash
   git tag -d v0.1.1
   git push origin :refs/tags/v0.1.1
   ```

Sideloaded testers keep their installed APK — Android does not auto-uninstall.
Push a v0.1.2 with the fix and message testers to update.

## Troubleshooting

- **"Verification failed: ZIP entries not in alphabetical order"** — APK tampered.
  Rebuild from clean: `./gradlew clean :app:assembleRelease`.
- **"Keystore was tampered with, or password was incorrect"** — `signing.properties`
  password mismatch. Verify against `keystore/PASSWORDS.txt`.
- **R8 strips a class** — Add a `-keep class <package>.<Class>` rule to
  `app/proguard-rules.pro` and rebuild.
- **Workflow can't find keystore** — Re-encode the keystore (`base64 -w 0`)
  and update `KEYSTORE_BASE64` repo secret.
