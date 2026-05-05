# MamY — Roadmap (2026-05-03 → V1 ship)

## Ce qui reste à shipper pour APK testable + partageable

### Wave 1 — 5 agents en parallèle (~5-7 j wall time)

**P7 UI Compose** (~5-7 j, 23 tasks restantes)
- Onboarding multi-step (permissions + calendar OAuth + BYOK + wake-word test + SMS opt-in)
- ReportsListScreen avec emotional trend + flags badge
- PersonDetailScreen avec sections (notes / promises / actions / flags / **SMS** ← P9)
- ActionsScreen avec swipe-to-done
- SettingsScreen modulaire (Account / BYOK / Briefings / Privacy / Cost / Lang / Wake-word / **SMS** / About)
- NetworkLogScreen (transparency)
- DataScreen (export / wipe + **SMS history** ← P9)
- VoiceIndicator overlay
- Theme adjustments accessibility
- Tests Compose UI

**P9 SMS Vocal Feature** (~5-7 j, ~18 tasks, design en cours)
- ContactsRepository (READ_CONTACTS + ContentResolver)
- ContactMatcher cascade (Person → exact → substring → Levenshtein)
- IntentGrammar extension `text_to` FR + EN
- TextToHandler orchestrator
- VoiceConfirmListener (Android SpeechRecognizer 3s)
- SmsSender (SmsManager direct + status callbacks)
- SentSmsEntry Room table + DAO + DB version=3
- Permission flow runtime
- HomonymeClarifier réutilisé
- Hilt modules ContactsModule + SmsModule

### Wave 2 — 1-2 agents (~2-3 j wall time)

**P8 minimal** (~2-3 j, signed APK sideload)
- Signing config + keystore release
- R8 / ProGuard rules pour Room/Hilt/Kotlin/SQLCipher/Whisper JNI/Picovoice
- GitHub Releases workflow + signed APK download
- App icon final 512×512 (déjà placeholder M-letter en P1)
- Privacy policy markdown draft + GitHub Pages hosting (optionnel V1)
- Final smoke checklist

### Total estimé

- **Wave 1** (5 agents parallèles) : ~5-7 jours
- **Wave 2** (signed APK + smoke) : ~2-3 jours
- **Total wall time** : **~7-10 jours** = ~1.5-2 semaines calendrier

## Distribution V1 alpha

```
GitHub Releases v0.1.0-alpha (privé)
↓
Tag + signed APK upload
↓
Lien direct distribué à 10-20 testeurs
↓
Testeurs activent "Install unknown apps" → tap install → onboarding
```

**Pas de Play Store** pour V1. Si traction → Play Store internal track en V1.5 (~5-7 j additionnels : privacy policy live + store assets + AAB upload + 10 emails allowlist).

## Backlog post-V1 (V1.1, V2, V3)

### V1.1 (~1-2 sem post-V1, si user retours positifs)
- Auto-retry pending SMS via WorkManager
- Multi-segment SMS confirmation re-prompt
- "MamY qu'est-ce que je viens de texter" recap
- Edit before send : "non, change le message en..."
- Microsoft Graph Outlook calendar
- BYOK Gemini si pas en V1
- Whisper-base option (qualité +)

### V2 (~3-6 mois post-V1, post-traction)
- Mode strict privacy (Phi-3 mini local, ~2 GB)
- Apple iCloud calendar
- Premium TTS BYOK (ElevenLabs / PlayHT)
- Audio archive opt-in (TTL configurable)
- **iOS port** (Swift natif)
- Mode redaction NER local (killer feature enterprise)
- Apple Watch companion
- SMS multi-phone disambiguation
- Cost display par segment SMS
- Bulk SMS ("MamY texte à toute mon équipe...")
- SMS scheduling

### V3 / Enterprise
- BYOK centralisé admin org
- Audit log signé HMAC exportable
- SSO / MDM (Intune / JAMF)
- Endpoint LLM self-hosted (Bedrock / Azure OpenAI / privé)
- Compliance reports

### Cutting list (NEVER per user)
- WhatsApp send/read
- Email send/read
- Phone call vocal
- Read incoming SMS
- App SMS par défaut

## Métriques de succès V1

- 10 alpha testers utilisent quotidiennement 4 semaines
- ≥ 5/10 disent « *je peux plus me passer* » à 4 semaines
- ≥ 70 % des debriefs vocaux structurés correctement
- ≥ 80 % des briefings pré-meeting jugés utiles
- < 10 % false-wake-word par jour
- ≥ 5 SMS vocaux envoyés/jour par tester actif (proxy d'usage SMS feature)

## Métriques V1 ship public (3 mois post-launch)

- 100 paid users Solo tier ($19/mois)
- < 5 % churn mensuel
- NPS > 40
- ≥ 1 client Team tier (5+ seats)
