# MamY Release Keystore

This directory holds the Android release signing keystore for MamY V1 alpha distribution.

## Files

- `mamy-release.jks` — release keystore (gitignored)
- `PASSWORDS.txt` — credentials for the placeholder keystore (gitignored)
- `README.md` — this file (committed)

## Why placeholder credentials?

For the alpha distribution we ship a placeholder keystore with a known password
(`mamydev2026`). This is fine for sideload distribution to a small group of testers
because the signing key only proves "all installs come from the same dev build" —
it is not used for Play Store identity until production.

Before public Play Store release, regenerate the keystore with a strong password
and store the credentials in a password manager (1Password / Bitwarden / etc.).

## How to regenerate

```bash
cd D:/mamy
keytool -genkeypair -v \
  -keystore keystore/mamy-release.jks \
  -alias mamy \
  -keyalg RSA -keysize 2048 -validity 9125 \
  -storepass <STRONG_PASSWORD> \
  -keypass <STRONG_PASSWORD> \
  -dname "CN=MamY,OU=Engineering,O=MamY,L=Paris,ST=IDF,C=FR"
```

Then update `signing.properties` (gitignored, in repo root) with the new password.

## How signing config is wired

`app/build.gradle.kts` reads `signing.properties` at the repo root if present.
If absent, release builds fall back to the debug signing config (so devs without
the keystore can still build a release-mode APK locally for QA).

See `signing.properties.example` for the field layout.

## CI / GitHub Releases

For GitHub Actions release pipeline (`.github/workflows/release-apk.yml`), the
keystore is base64-encoded and stored as the `KEYSTORE_BASE64` repo secret. The
workflow decodes it at build time. See `docs/setup/release.md` for the secret
setup steps.

## Never commit

The following are gitignored and must NEVER be checked in:

- `keystore/*.jks`
- `keystore/PASSWORDS.txt`
- `signing.properties` (repo root)
