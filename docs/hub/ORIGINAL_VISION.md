# MamY — Vision originale (récap, rien n'a été oublié)

Ce doc rassure que le wedge initial du brainstorm 2026-05-02 est **TOUT CODÉ et SHIPPÉ** dans P1-P6.

## Wedge original (validé par user 2026-05-02)

> Manager de 30-100 employés finit son 1:1 en salle de réu, marche dans le corridor, parle 60-90 sec à son téléphone (« *MamY, prends note...* »), et l'app structure tout par employé : état émotionnel, promesses faites des deux côtés, actions à suivre. Avant chaque 1:1 suivant, briefing vocal de 30 sec sur ce qu'il faut savoir. Promesses qui dérapent → relance auto.

## Décisions verrouillées D1-D13 (toutes respectées en code)

| # | Décision | Where in code |
|---|---|---|
| D1 | Cible : managers 30-100 employés | spec section 3 (persona Marc Tremblay) |
| D2 | Wedge : capture passive post-meeting (debrief vocal) | P2 CapturePipeline + P3 LlmStructurer |
| D3 | Pas d'écoute live, juste post-meeting debrief | architecture P2 (no Zoom/Teams bot) |
| D4 | Plateforme V1 : Android-only Kotlin | tout le repo, P1 setup |
| D5 | Stack : Kotlin natif (pas Flutter/RN) | P1 confirmed |
| D6 | AI : BYOK Claude/GPT/Gemini, hybrid default | P3 LlmProvider sealed |
| D7 | Always-on Porcupine "MamY" wake-word | P2 PorcupineWakeWordEngine |
| D8 | Briefing : combo daily 8h + pré-meeting 5 min + on-demand | P6 DailyBriefingWorker + PreMeetingScheduler |
| D9 | Voice-first level B (voice-primary + UI minimal) | P7 UI consultation-only (WIP) |
| D10 | Naming produit : MamY | partout |
| D11 | Langue UI V1 : FR + EN | i18n strings.xml + values-fr/ |
| D12 | Géo V1 : Canada → USA → EU | targeting respecté |
| D13 | Subscription Solo $19/mois (pas freemium) | future P8 monetization |

## Les 4 types de briefing (P6 SHIPPED)

C'est ÇA dont tu parles avec « les memo briefing et tout » — c'est tout là, en code, fonctionnel.

### 1. Briefing matinal 8h (« MamY, ma journée »)
- Trigger : WorkManager `DailyBriefingWorker` à l'heure config (default 8h, ajustable)
- OU vocal on-demand
- Contenu : tous les 1:1s du jour, pour chaque personne :
  - état émotionnel récent
  - promesses ouvertes des 2 côtés
  - flags actifs (démotivation, conflit, risque)
  - dernière interaction (date, contexte)
- Source : `DailyBriefHandler` (P6) + `BriefingPromptBuilder` + LLM
- TTS : Android natif FR ou EN selon settings

### 2. Briefing pré-meeting 5 min avant (notif silencieuse)
- Trigger : `PreMeetingScheduler` (1-min check loop) → 5 min avant un calendar event mappé
- Notif silencieuse + vibration → user peut tap ou dire « *MamY, briefe* »
- Contenu : 1 personne, contexte récent + promesses bilatérales + risques flaggés
- Source : `PreMeetingBriefHandler` (P6)

### 3. Person query (« MamY, briefe-moi sur Marie »)
- Trigger : voice intent `person_brief` → `PersonQueryBriefHandler`
- Real-time, pas de cache
- Contenu : tout sur la personne (notes, promises, actions, flags, calendar history)
- LLM compose un briefing narratif

### 4. EOD summary (« MamY, résume ma journée »)
- Trigger : voice intent `eod_summary` → `EodSummaryHandler`
- Contenu : 1:1s de la journée + actions générées + promises updated + flags nouveaux

## Mémoire bilatérale (P3 + P4 SHIPPED)

- **Promises** table tracked **dans les 2 sens** :
  - `Promise.from = self, to = person_id` (toi → ton report)
  - `Promise.from = person_id, to = self` (ton report → toi)
- Voice intent « *MamY, qui me devait quoi* » → `PromisesOwedMeHandler` (P4)
- Voice intent « *MamY, mes actions ouvertes* » → `ActionsOpenHandler` (P4)
- Status enum : `active | kept | broken | renegotiated`
- Flags par personne : `demotivation | conflict | risk | opportunity | burnout | growth`
- Source detection : direct (le report te l'a dit) OU indirect (Marie t'a dit que Pierre est démotivé)

## Voice command grammar (P4 SHIPPED, 10 intents)

Tous codés et testables :
1. `capture` — « MamY, prends note... »
2. `daily_brief` — « MamY, ma journée »
3. `next_brief` — « MamY, briefe »
4. `person_brief` — « MamY, briefe-moi sur X » / « c'est quoi avec X »
5. `promises_owed_me` — « MamY, qui me devait quoi »
6. `actions_open` — « MamY, mes actions ouvertes »
7. `eod_summary` — « MamY, résume ma journée »
8. `undo_last` (≤30s) — « MamY, oublie ça »
9. `correct_last` — « MamY, modifie : ... »
10. `unknown` (fallback) — TTS « pas compris »

**Ajout en cours (P9 brainstormé 2026-05-03)** :
11. `text_to` — « MamY, texte à <X> que <message> » → SMS direct via SmsManager

## Calendar Google (P5 SHIPPED)

- OAuth 2.0 + scope `calendar.readonly`
- Sync 15 min via WorkManager
- Person matching auto depuis attendees emails
- Mode no-calendar fonctionnel (briefings on-demand uniquement)
- Token storage : EncryptedSharedPreferences

## Privacy stance (D6 + Section 7 spec)

- Audio brut : RAM only, jamais stocké, jamais cloud (Whisper local on-device)
- Texte structuré : DB SQLCipher locale chiffrée
- BYOK keys : Android Keystore master AES-256 hardware-backed
- 3 modes user-selectable : Standard (cloud LLM) · Strict (Phi-3 local, V2) · Hybrid+redaction (NER local, V2 enterprise)
- RGPD/Loi 25 : OK by design

## Si tu doutes encore : checklist concrète

```bash
cd D:\ComfyUI-Intel\mamy
ls app\src\main\kotlin\com\mamy\android\domain\briefing\
# DailyBriefHandler.kt
# PreMeetingBriefHandler.kt
# PersonQueryBriefHandler.kt
# EodSummaryHandler.kt
# BriefingGenerator.kt
# BriefingPromptBuilder.kt
# ContextAssembler.kt
# BriefingCache.kt

ls app\src\main\kotlin\com\mamy\android\domain\intent\handler\
# CaptureHandler.kt
# PromisesOwedMeHandler.kt
# ActionsOpenHandler.kt
# UndoLastHandler.kt
# CorrectLastHandler.kt
# HomonymeClarifier.kt
# IntentHandler.kt
# (etc.)

ls app\src\main\kotlin\com\mamy\android\service\work\
# DailyBriefingWorker.kt
# PreMeetingScheduler.kt
```

→ Tout est là. Le wedge debrief + briefings est **vivant en code**.
