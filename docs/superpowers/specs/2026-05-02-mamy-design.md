# MamY — Design Spec

**Date** : 2026-05-02
**Statut** : Design validé par user (sections 1-7), prêt pour writing-plans.
**Auteur** : Brainstorm collaboratif via skill `superpowers:brainstorming`.
**Repo** : `D:\ComfyUI-Intel\mamy\` · GitHub privé `sxc3030-eng/mamy` · branch `main`.

---

## 1. Vision & pitch

### Pitch (1 phrase)
> **MamY** est une app Android *voice-first* qui transforme le debrief vocal post-1:1 en mémoire vivante par employé pour managers de 30 à 100 personnes — briefe avant chaque rencontre, traque les promesses qui dérapent.

### Wedge V1
**Capture passive post-meeting** en debrief vocal libre (60-90 sec). Pas d'écoute live de la réunion (pas de friction légale, pas de bot Zoom/Teams), pas de prise de note manuelle, pas d'écran à toucher.

### Pourquoi MamY existe
- Un manager de 30-100 personnes fait 30-50 1:1s par mois, oublie tout, loupe les promesses, n'a pas le temps de tenir un Notion à jour
- Les outils existants (Granola, Otter, Fellow, Lattice) demandent soit (a) que tu enregistres la réunion, soit (b) que tu écrives ta note structurée
- MamY ne demande ni l'un ni l'autre : *tu marches, tu parles, c'est fait*

### Ce que MamY n'est PAS (anti-scope)
- ❌ Un outil RH/HRIS (BambooHR, Lattice)
- ❌ Un transcripteur de réunions (Otter, Granola)
- ❌ Un PM tool (Asana, Monday)
- ❌ Un assistant générique (Siri, Alexa)
- ❌ Un CRM
- ❌ Un outil collaboratif (zéro partage social)

---

## 2. Décisions verrouillées

| # | Décision | Choix | Raison |
|---|---|---|---|
| D1 | Cible | Managers d'équipes 30-100 employés | Sweet spot : ils ont le volume mais pas l'org HR pour avoir un assistant humain |
| D2 | Wedge V1 | Capture passive post-meeting (debrief vocal libre) | Différencie de Granola/Otter, zéro friction légale |
| D3 | Mode capture V1 | Debrief post-meeting uniquement | Pas d'écoute live, pas de bot Zoom/Teams (V1+) |
| D4 | Plateforme V1 | Android-only (Kotlin natif) | User dev sur Windows, doit dogfooder |
| D5 | Stack | Kotlin natif, Jetpack Compose, API 28+ | Wake-word + audio fiable, iOS demandera Swift de toute façon |
| D6 | AI / Privacy | BYOK Claude/GPT/Gemini, hybrid default (Whisper local + LLM cloud), full-local opt-in | Privacy first + flexibilité user |
| D7 | Always-on listening | Foreground service + Picovoice Porcupine wake-word « MamY » | Best-in-class wake-word custom, low CPU |
| D8 | Briefing | Combo daily 8h + pré-meeting 5 min + on-demand. Tout ajustable. | Voice-first cohérent, respect du contexte |
| D9 | Voice-first level | Voice-primary + UI minimal (option B) | Capture/queries vocal, UI pour consultation dense (liste 45 reports) |
| D10 | Naming produit | **MamY** (wake-word = nom commercial) | User decision 2026-05-02 |
| D11 | Langue UI V1 | FR + EN | Marché B2B pro NA majoritairement EN, base francophone à servir |
| D12 | Géo V1 | Canada (Quebec + ROC) → USA V1.5 → EU V2 | Loi 25 OK by design, dogfood facile |
| D13 | Modèle business | Subscription (Solo / Team / Enterprise) + trial 14j. Pas de freemium permanent. | Le wedge complet est la value, pas de demi-pain |

---

## 3. Persona & journée type

### Persona principale : Marc Tremblay, 42 ans
Directeur d'opérations chez une PME manufacturière de 280 personnes à Drummondville. Manage 6 reports directs (chefs d'équipe), qui eux managent ~45 indirects. Fait ~25 1:1s par mois. Drive 30 min entre 2 sites. Andoid Pixel 7. A déjà essayé Notion (lâché), Otter (lâché), Asana (utilise pour projets seulement).

### Pain points actuels
- Oublie ce que Marie lui a dit la dernière fois en 1:1 (« on parlait de quoi déjà ? »)
- Promet de relancer RH pour Pierre, oublie pendant 2 semaines, Pierre est démotivé
- Lit 3 lignes de notes sur son tel avant le 1:1, zéro contexte
- Personne ne lui dit que Pierre est démotivé jusqu'à ce que Pierre démissionne

### Journée type avec MamY

**8h00 — Café à la maison.**
« *MamY, ma journée* » → 60 sec vocal :
> « 5 1:1s aujourd'hui. Marie 10h — stressée projet X la dernière fois, tu devais relancer RH, c'est fait depuis. Pierre 11h30 — il t'avait promis le mockup vendredi passé, pas reçu. Julie 14h — première fois, nouveau report. Luc 15h — rien d'urgent. Anaïs 16h — elle attend ton feedback sur sa proposition. »

**9h55 — Vibration silencieuse, pré-1:1 Marie.**
« *MamY, briefe* » → 20 sec :
> « Marie, dernier 1:1 il y a 8 jours, projet X la stresse, livrable vendredi. RH a confirmé hier que sa demande de transfert interne est en cours. Promesse : tu lui as promis 30 min cette semaine pour reviewer son CV — c'est aujourd'hui. »

**10h45 — Sortie 1:1, Marc marche vers son bureau.**
« *MamY, prends note. Marie va mieux, RH a confirmé son transfert pour juin, on a parlé du CV ensemble, elle veut postuler interne pour le rôle de lead. Faut que je parle à David du dept produit pour vérifier si ça matche son ouverture. Aussi, elle m'a dit que Pierre traîne sur le mockup parce qu'il est démotivé.* »

→ MamY structure et confirme :
> « Noté. 1 action (parler à David, sans deadline), 1 personne flaggée (Pierre, démotivation indirecte). »

**11h25 — Vibration. Briefing pré-Pierre :**
> « Pierre, mockup promis vendredi passé, pas reçu. Source indirecte : Marie t'a dit ce matin qu'il est démotivé. À creuser doucement. »

**18h00 — Fin de journée.**
« *MamY, résume* » → 45 sec :
> « 5 1:1s, 7 actions générées dont 2 ouvertes (parler David, suivi Pierre démotivation). 1 promesse passée à toi par Anaïs tenue. 1 promesse de toi à un client due demain. RAS sur les deals en cours. »

---

## 4. Architecture technique

### Stack
- **Langage** : Kotlin 2.x
- **UI** : Jetpack Compose (Material 3, big-text accessibility-friendly)
- **Async** : Coroutines + Flow
- **DI** : Hilt
- **DB** : Room + SQLCipher (encrypted)
- **Background** : `WorkManager` + Foreground Service
- **Target SDK** : 35 (Android 15) · **Min SDK** : 28 (Android 9, ~95 % devices actifs)

### Composants (vue blocs)

```
┌──────────────────────────────────────────────────────┐
│  UI (Jetpack Compose, Material 3)                    │
│  • OnboardingScreen   • ReportsListScreen            │
│  • PersonDetailScreen • ActionsScreen                │
│  • SettingsScreen     • NetworkLogScreen             │
└──────────────────────────────────────────────────────┘
                         ↓↑
┌──────────────────────────────────────────────────────┐
│  Foreground Service "MamYListener"                   │
│  (notif permanente, ~3-5 % CPU continu)              │
│                                                       │
│  ┌─────────────────────────────────────────────┐     │
│  │ WakeWordEngine (Picovoice Porcupine)        │     │
│  │ • Modèle "MamY" custom (~100 KB on-device)  │     │
│  │ • Sensibilité user-tunable                  │     │
│  └─────────────────────────────────────────────┘     │
│              ↓                                        │
│  ┌─────────────────────────────────────────────┐     │
│  │ AudioCapture + VAD (WebRTC VAD)             │     │
│  │ • AudioRecord 16 kHz mono PCM               │     │
│  │ • Silence cut 1.5 sec, max 90 sec           │     │
│  └─────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────┐
│  STT local (whisper.cpp Android, JNI)                │
│  • whisper-tiny multilingual (~75 MB, FR + EN)       │
│  • Inference ~1-2 sec / 60 sec audio sur Pixel 6+    │
└──────────────────────────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────┐
│  IntentRouter (regex/keyword sur texte STT)          │
│  • 10 intents fixes + fallback `capture`             │
└──────────────────────────────────────────────────────┘
                         ↓
        ┌─────────────────────────┬───────────────────┐
        ↓                         ↓                   ↓
┌───────────────────┐  ┌──────────────────┐  ┌────────────────┐
│ LLM Structurer    │  │ BriefingGenerator│  │ QueryHandler   │
│ (cloud BYOK)      │  │ (cloud BYOK)     │  │ (DB local)     │
│ → JSON structuré  │  │ → texte vocal    │  │ → texte vocal  │
└───────────────────┘  └──────────────────┘  └────────────────┘
        ↓                         ↓                   ↓
┌──────────────────────────────────────────────────────┐
│  Storage local : Room + SQLCipher (encrypted)        │
│  Tables : Person, Note, Action, Promise, Flag,       │
│           Meeting, MeetingAttendee, Briefing         │
└──────────────────────────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────┐
│  TTS (Android TextToSpeech natif, voix FR/EN)        │
└──────────────────────────────────────────────────────┘
```

### Permissions Android requises
- `RECORD_AUDIO` (always-on micro)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`
- `POST_NOTIFICATIONS`
- `INTERNET`
- `WAKE_LOCK` (light, briefing matinal)
- Calendar : OAuth Google Calendar API + Microsoft Graph (V1.1) — pas de permission system-level si on utilise les APIs cloud

### Footprint
- APK + assets : ~50 MB
- Modèles (téléchargés first-run) : whisper-tiny ~75 MB + Porcupine ~100 KB
- **Total install** : ~125 MB
- RAM idle : 80-120 MB (FGS)
- Battery : ~5-8 %/jour sur tel récent

---

## 5. Capture & structuration (le cœur)

### Wake-word « MamY »
- **Engine** : Picovoice Porcupine, modèle custom (entraîné via Picovoice Console, ~100 KB binaire on-device)
- **Sensibilité** : 3 niveaux user-tunable (low / medium / high), default = medium
- **Indicateur** : icône notif foreground change quand wake-word fire (point gris → vert pulsant)
- **Fallback bouton** : long-press volume-up (1 sec) → bypass wake-word (mode silencieux/réunion/raté)

### Capture audio
- `AudioRecord` 16 kHz mono PCM (format Whisper natif, pas de resampling)
- **VAD** : WebRTC VAD lib, coupe après 1.5 sec silence
- **Max** : 90 sec hard cap (« *MamY : enregistrement coupé, continue* »)
- Audio en RAM seulement pendant le pipeline (default), jamais sur disque

### STT local
- `whisper.cpp` compilé NDK + bindings JNI
- **V1** : `whisper-tiny` multilingual (FR + EN, ~75 MB), bundle dans APK ou téléchargé first-run
- Inference ~1-2 sec pour 60 sec audio sur Pixel 6+
- **V2 opt-in** : `whisper-base` (~140 MB, qualité +)

### Voice command grammar (parser local sur le texte STT, FR + EN)

| FR | EN | Intent | Action |
|---|---|---|---|
| « MamY, prends note... » | « MamY, take a note... » | `capture` | mode debrief libre, structuration LLM |
| « MamY, ma journée » | « MamY, my day » | `daily_brief` | briefing matinal vocal |
| « MamY, briefe » | « MamY, brief me » | `next_brief` | briefing pré-meeting (next event) |
| « MamY, briefe-moi sur \<X\> » | « MamY, brief me on \<X\> » | `person_brief` | pull contexte personne |
| « MamY, c'est quoi avec \<X\> » | « MamY, what's up with \<X\> » | `person_brief` (alias) | idem |
| « MamY, qui me devait quoi » | « MamY, what's owed to me » | `promises_owed_me` | list promesses entrantes ouvertes |
| « MamY, mes actions ouvertes » | « MamY, my open actions » | `actions_open` | list actions à faire |
| « MamY, résume ma journée » | « MamY, summarize my day » | `eod_summary` | résumé fin de journée |
| « MamY, oublie ça » (≤30 sec) | « MamY, forget that » | `undo_last` | annule dernière capture |
| « MamY, modifie : ... » | « MamY, edit: ... » | `correct_last` | re-soumet au LLM avec correction |

Si aucun pattern ne matche → fallback intent `capture` (debrief libre).

### Structuration LLM (intent `capture`)

System prompt envoyé au LLM cloud BYOK avec le texte STT :

```
Tu es l'assistant secrétaire d'un manager d'équipe 30-100 personnes. Tu reçois
un debrief vocal libre post-meeting (FR ou EN). Extrait en JSON strict :

- persons : nom, role_hint, emotional_state (ok|stressed|demotivated|happy|conflict|...)
- actions : description, assignee (self ou nom), deadline ISO8601 ou null, linked_person
- promises : from, to, what, due
- flags : person, type (demotivation|conflict|risk|opportunity|...), source (direct|indirect:X), note
- meeting_meta : person_main (avec qui était le 1:1, déduit), date_inferred

Si une info est ambiguë, mets null plutôt que d'inventer. Réponds JSON brut, sans markdown.
```

JSON schema (strict) :

```json
{
  "persons": [{
    "name": "string",
    "role_hint": "string|null",
    "emotional_state": "ok|stressed|demotivated|happy|conflict|engaged|disengaged",
    "context_added": "string"
  }],
  "actions": [{
    "description": "string",
    "assignee": "self|<person_name>",
    "deadline": "ISO8601|null",
    "linked_person": "<person_name>|null"
  }],
  "promises": [{
    "from": "self|<person_name>",
    "to": "self|<person_name>",
    "what": "string",
    "due": "ISO8601|null"
  }],
  "flags": [{
    "person": "<person_name>",
    "type": "demotivation|conflict|risk|opportunity|burnout|growth",
    "source": "direct|indirect:<person_name>",
    "severity": "low|medium|high",
    "note": "string"
  }],
  "meeting_meta": {
    "person_main": "<person_name>|null",
    "date_inferred": "ISO8601|null"
  }
}
```

JSON parsé → Room DB encryptée. Si parsing échoue → texte stocké brut + flag `non_structured=true` (à réviser plus tard manuellement).

### Confirmation post-capture
- TTS Android natif voix FR ou EN selon langue UI : « *Noté. 2 actions, 1 personne flaggée.* » / « *Noted. 2 actions, 1 person flagged.* »
- Mode silencieux possible (settings) : juste vibration
- Latence totale (wake-word → confirmation) : ~3-5 sec sur tel récent

---

## 6. Mémoire vivante & briefing

### Schéma DB (Room + SQLCipher)

```kotlin
@Entity Person(
  id: UUID PK,
  name: String,
  email: String?,                    // unique-ish, set when calendar matched
  role_hint: String?,
  calendar_attendee_id: String?,     // attendee ID/email from calendar event
  created_at: Instant,
  last_interaction_at: Instant?,
  interaction_count: Int,
  emotional_trend: String?,          // last N states summarized
  unmatched: Boolean = true,         // flagged until user confirms
  archived: Boolean = false
)

@Entity Note(
  id: UUID PK,
  person_id: UUID? FK Person,
  meeting_id: UUID? FK Meeting,
  raw_text: String,                  // Whisper transcript
  structured_json: String?,          // LLM JSON output (null if parse failed)
  non_structured: Boolean = false,
  created_at: Instant,
  audio_duration_sec: Int,
  llm_provider: String,              // claude|gpt|gemini|local
  llm_cost_cents: Int?
)

@Entity Action(
  id: UUID PK,
  description: String,
  assignee: String,                  // "self" or person_id as string
  linked_person_id: UUID? FK Person,
  deadline: Instant?,
  status: String,                    // open|done|snoozed|dropped
  from_note_id: UUID FK Note,
  created_at: Instant,
  done_at: Instant?
)

@Entity Promise(
  id: UUID PK,
  from_id: String,                   // "self" or person_id
  to_id: String,                     // "self" or person_id
  what: String,
  due: Instant?,
  status: String,                    // active|kept|broken|renegotiated
  from_note_id: UUID FK Note,
  created_at: Instant,
  resolved_at: Instant?
)

@Entity Flag(
  id: UUID PK,
  person_id: UUID FK Person,
  type: String,                      // demotivation|conflict|risk|opportunity|burnout|growth
  source: String,                    // direct or indirect:<person_id>
  severity: String,                  // low|medium|high
  note: String,
  resolved: Boolean = false,
  from_note_id: UUID FK Note,
  created_at: Instant
)

@Entity Meeting(
  id: UUID PK,
  calendar_event_id: String?,
  title: String,
  starts_at: Instant,
  ends_at: Instant,
  briefing_text: String?,
  post_note_id: UUID? FK Note,
  created_at: Instant
)

@Entity MeetingAttendee(
  meeting_id: UUID FK Meeting,
  person_id: UUID FK Person,
  PRIMARY KEY (meeting_id, person_id)
)

@Entity Briefing(  // cache pour éviter regen
  id: UUID PK,
  type: String,                      // daily|pre_meeting|person_query|promises_owed|actions_open|eod_summary
  target_id: String?,                // meeting_id|person_id|null
  generated_at: Instant,
  expires_at: Instant,
  text: String,
  llm_provider: String,
  llm_cost_cents: Int?
)
```

Tout chiffré via SQLCipher (passphrase dérivée de master key Android Keystore, hardware-backed quand dispo).

### Calendar sync

- **Providers V1** : Google Calendar (OAuth scope `calendar.readonly`)
- **V1.1** : Microsoft Graph (scope `Calendars.Read`)
- **V2** : Apple iCloud
- **Strategy** : initial = 30 jours passés + 30 futurs ; delta sync via `WorkManager` toutes les 15 min ; pull-to-refresh manuel
- **Mode no-calendar** : tout fonctionne sans, briefings disponibles seulement on-demand vocal

### Person matching (event ↔ Person DB)

1. Match par `attendee.email` → `Person.calendar_attendee_id`
2. Pas de hit → créer `Person(unmatched=true)` avec nom déduit de l'email (`marc.tremblay@x.com` → « Marc Tremblay »)
3. Au premier 1:1 avec ce stub, prompt UI rapide : « *Nouvelle personne : Marc Tremblay. Tu confirmes ?* »
4. Homonymes : si capture sans calendar event actif et 2+ matches → TTS clarification : « *Tu parles de Marie Dubois ou Marie Tremblay ?* » (user répond vocal ou tap)

### Briefing generation (pipeline)

Pour chaque type de briefing :

```
1. Query Room DB pour assembler le contexte :
   - daily : tous les meetings du jour, attendees mappés, last 3 notes/each, open promises both ways, open flags
   - pre_meeting : 1 personne, last 5 notes, open promises both ways, open flags
   - person_query : tout sur la personne
   - eod_summary : notes du jour + actions générées + promises updated

2. Assemble prompt LLM cloud BYOK :
   "Tu briefes un manager. Contexte : <JSON>. Génère un texte vocal de
    <N> sec, ton conversationnel, pas de redondance avec ce que le manager
    sait déjà, mets l'accent sur les promesses ouvertes et les flags."

3. LLM retourne texte → cache dans Briefing(expires_at) → TTS Android natif → audio out
```

### Cache briefing
- `daily` : TTL 8h
- `pre_meeting` : TTL 1h
- `person_query` : aucun cache (toujours real-time)
- `eod_summary` : aucun cache

### TTS
- **V1** : Android `TextToSpeech` natif, voix FR ou EN selon UI lang (gratuit, OK qualité)
- **V2 opt-in** : voix premium BYOK (ElevenLabs / PlayHT, ~5-15 $/mois) pour qualité naturelle

### Cost tracking BYOK
Chaque appel LLM logge `llm_provider` + `llm_cost_cents`. Settings écran : « *Coût ce mois : 4,30 $ (Claude) · 0,80 $ (Gemini)* ». Critique pour transparence pro.

---

## 7. Privacy & BYOK

### Data lifecycle

| Donnée | Localisation | Persistance | Crypto |
|---|---|---|---|
| Audio brut | RAM | ~5 sec | n/a |
| Audio archive (opt-in) | Local disk | TTL configurable | SQLCipher |
| Transcript Whisper | Room DB | Forever (jusqu'à delete user) | SQLCipher |
| Structured JSON | Room DB | Forever | SQLCipher |
| Briefings cache | Room DB | TTL 1-8h | SQLCipher |
| **BYOK API keys** | Android Keystore | Forever | **Hardware-backed AES** |
| Calendar OAuth tokens | EncryptedSharedPreferences | Auto-refresh | EncryptedSP |
| Settings preferences | DataStore | Forever | Plain (non-sensitive) |

**Audio = jamais stocké par défaut, jamais cloud.**

### BYOK config
- Providers V1 : Anthropic Claude · OpenAI GPT-4o · Google Gemini · Local (Phi-3 mini, V2)
- Settings : dropdown provider + API key + (optional) endpoint override (enterprise self-hosted)
- Validation au save : test call avec 1 token (~0.0001 $)
- Cost tracker visible

### 3 modes privacy (user-selectable)

- **Standard (default)** : Whisper local → texte → LLM cloud BYOK → JSON. Texte structuré transit en clair via TLS.
- **Strict (V2 opt-in)** : Phi-3 mini on-device pour structuration. Zero call cloud. ~2 GB modèle, qualité un cran sous, batterie un peu plus.
- **Hybrid + redaction (V2 enterprise)** : NER local extrait noms → placeholders (`Person_42`) → LLM voit anonymisé → réponse remappée localement. *« Vos noms d'employés ne quittent jamais le téléphone en clair »*.

### Network calls inventory
UI debug `NetworkLogScreen` liste **tous** les outbound :
1. LLM provider (per debrief structuré + per briefing généré)
2. Calendar OAuth token refresh
3. Calendar API delta sync
4. (V2) Premium TTS calls

Aucun analytics, aucune telemetry, aucun crash reporter cloud V1. Crashes → log local, user envoie manuellement si veut.

### Data portability + droit à l'oubli (RGPD / Loi 25)
- **Export tout** : JSON gzipped + AES-passphrase user → backup/migration
- **Wipe all** : un bouton settings, tout disparaît
- **Wipe per person** : long-press dans liste reports → « Effacer cette personne et tout son historique »

### Compliance
- **Loi 25 Quebec** : OK by design (consent explicite, droit à l'oubli, portabilité, no transfer hors juridiction sans consent)
- **PIPEDA Canada** : OK by design
- **GDPR EU** : OK by design (V2 launch)
- **HIPAA USA** : non couvert V1 (pas de target médical V1)

---

## 8. MVP scope V1 / V2 / V3

### V1 (ship public ~3-4 mois solo + Claude)

✅ Wake-word custom MamY (Porcupine)
✅ Foreground service always-on
✅ Whisper-tiny FR+EN bundled
✅ VAD + cap 90 sec
✅ 10 voice intents FR+EN
✅ Fallback bouton volume-up
✅ BYOK Claude + GPT (Gemini = V1.1 si flemme)
✅ Mode standard privacy uniquement (cloud LLM)
✅ Schéma DB complet (8 tables : Person, Note, Action, Promise, Flag, Meeting, MeetingAttendee, Briefing)
✅ SQLCipher + Android Keystore
✅ Person stub auto-creation
✅ Daily morning briefing 8h ajustable
✅ Pre-meeting briefing 5 min + notif silencieuse
✅ Person query vocal
✅ EOD summary
✅ TTS Android natif
✅ Google Calendar OAuth + sync 15 min
✅ Mode no-calendar fonctionnel
✅ UI Compose : Reports list, Person detail, Actions, Settings, NetworkLog, Onboarding
✅ Cost tracker BYOK
✅ Export tout / Wipe / Wipe per person
✅ Android 9+ (API 28+)
✅ APK + Google Play closed beta puis open

### V1.1 (~4-6 sem post-V1)

- Microsoft Graph (Outlook calendar)
- BYOK Gemini si pas en V1
- Whisper-base option (qualité +)

### V2 (~3-6 mois post-V1)

- Mode strict privacy (Phi-3 mini local, ~2 GB)
- Apple iCloud calendar
- Premium TTS BYOK (ElevenLabs / PlayHT)
- Audio archive opt-in (TTL configurable)
- **iOS port** (Swift natif, partage prompts/schémas/backend logic)
- Mode redaction NER local (killer enterprise feature)
- Apple Watch companion (briefing en tap watch)

### V3 / Enterprise

- BYOK centralisé (admin org → N seats)
- Audit log signé HMAC exportable
- SSO / MDM (Intune / JAMF)
- Endpoint LLM self-hosted (Bedrock / Azure OpenAI / LLM privé)
- Compliance reports

### Cutting list (NEVER, principe)

- ❌ Comptes cloud user (zero login = privacy)
- ❌ Partage social / collaboration entre users
- ❌ Enregistrement live des réunions (V1 — consent legal trop complexe)
- ❌ Chat avec d'autres users
- ❌ Analytics user behavior

---

## 9. Modèle business

| Tier | Prix | Cible | Inclus |
|---|---|---|---|
| **Trial** | Gratuit 14j | Découverte | Toutes features V1, BYOK obligatoire dès jour 1 |
| **Solo** | 19 $/mois ou 190 $/an | Manager indép. / freelance équipe | Tout V1, support email |
| **Team** | 15 $/seat/mois (min 5 seats, annuel) | PME 30-100 employés, plusieurs managers | + admin BYOK centralisé + audit log basic |
| **Enterprise** | 35-50 $/seat/mois (annuel custom) | Grosses boîtes | + SSO + MDM + endpoint privé + HMAC audit |

**Pas de freemium permanent.** Le wedge complet (capture + briefing illimité) *est* la value : un free tier permanent vendrait du « petit pain » qui tue le pitch.

**Avantage prix BYOK** : compute non inclus → on peut être 5-8 $ moins cher que Granola/Otter/Fellow qui bundle compute, et le user paie son propre token (transparence + scaling).

---

## 10. Metrics de succès

### V1 (alpha-beta)
- 10 alpha testers (managers réels) utilisent quotidiennement pendant 4 semaines
- ≥ 5/10 disent « *je peux plus me passer de ça* » au check-in semaine 4
- ≥ 70 % des debriefs vocaux sont structurés correctement (validés par user)
- ≥ 80 % des briefings pré-meeting sont jugés utiles (1-tap rating dans UI)
- < 10 % de false-wake-word par jour

### V1 ship public (3 mois post-launch)
- 100 paid users Solo tier
- < 5 % churn mensuel
- NPS > 40
- ≥ 1 client Team tier (5+ seats)

---

## 11. Open questions (à résoudre avant ou pendant impl)

1. **Picovoice Porcupine pricing à scale** : free tier limité, payant à scale (~$$/user/an). Si trop cher, alternative open-source = OpenWakeWord ou Vosk (qualité un cran sous, training custom à faire). Décision : V1 → Porcupine, monitor cost, V2 → alternative si pricing pète.
2. **Whisper-tiny FR quality** : à valider sur dataset réel (jargon manager québécois). Si insuffisant, fallback whisper-base (~140 MB) ou cloud STT (Deepgram, Speechmatics) en option.
3. **Provider LLM par défaut V1** : Claude (matche écosystème user) ou GPT (plus de devs familiers) ? Probablement les 2 disponibles, default = Claude.
4. **Pricing exact** : 19 $/mois est ma reco — à valider avec 5-10 prospects en interview avant ship public.
5. **Géo USA launch** : V1.5 ou V1 dès le start ? Suggéré V1.5 (post-validation Canada), mais réversible si Canada slow.
6. **Voix TTS de base FR** : la voix native Android FR varie par OEM (Samsung, Pixel, OnePlus...). Test cross-device requis.
7. **Wake-word multi-langue** : « MamY » fonctionne en FR ET EN avec Porcupine custom ? À valider — pourrait nécessiter 2 modèles (FR-MamY, EN-MamY) selon langue UI.

---

## 12. Glossaire

- **Wake-word** : phrase qui réveille l'app (ici « MamY »)
- **VAD** : Voice Activity Detection (détecte silence pour couper l'enreg.)
- **STT** : Speech-To-Text
- **TTS** : Text-To-Speech
- **BYOK** : Bring Your Own Key (user fournit sa clé API LLM)
- **NER** : Named Entity Recognition (extraction noms propres)
- **Foreground Service** : service Android qui tourne en background avec notif permanente, droit d'utiliser le micro 24/7
- **Whisper** : modèle STT open-source d'OpenAI, version `tiny`/`base`/`small`/...
- **Porcupine** : wake-word engine de Picovoice
- **Phi-3 mini** : LLM open-weights de Microsoft, ~3.8B params, capable on-device
- **1:1** : meeting one-on-one entre manager et report
- **Promesse bilatérale** : promesses des 2 côtés (toi → report ET report → toi)

---

## 13. Annexes

### A. Voice grammar regex (parser local)

```kotlin
val intents = mapOf(
  Regex("^MamY,?\\s+(prends|take a)\\s+note", IGNORE_CASE) to Intent.CAPTURE,
  Regex("^MamY,?\\s+(ma journée|my day)", IGNORE_CASE) to Intent.DAILY_BRIEF,
  Regex("^MamY,?\\s+(briefe(?!\\s+moi)|brief me)\\s*$", IGNORE_CASE) to Intent.NEXT_BRIEF,
  Regex("^MamY,?\\s+(briefe-moi sur|brief me on)\\s+(.+)", IGNORE_CASE) to Intent.PERSON_BRIEF,
  Regex("^MamY,?\\s+(c'est quoi avec|what's up with)\\s+(.+)", IGNORE_CASE) to Intent.PERSON_BRIEF,
  Regex("^MamY,?\\s+(qui me devait quoi|what's owed to me)", IGNORE_CASE) to Intent.PROMISES_OWED_ME,
  Regex("^MamY,?\\s+(mes actions ouvertes|my open actions)", IGNORE_CASE) to Intent.ACTIONS_OPEN,
  Regex("^MamY,?\\s+(résume ma journée|summarize my day)", IGNORE_CASE) to Intent.EOD_SUMMARY,
  Regex("^MamY,?\\s+(oublie ça|forget that)", IGNORE_CASE) to Intent.UNDO_LAST,
  Regex("^MamY,?\\s+(modifie|edit)\\s*:?\\s*(.+)", IGNORE_CASE) to Intent.CORRECT_LAST,
)
// Si aucun match → fallback Intent.CAPTURE (mode debrief libre)
```

### B. Module structure proposée

```
app/
├── ui/                         # Jetpack Compose screens
│   ├── onboarding/
│   ├── reports/                # ReportsListScreen + PersonDetailScreen
│   ├── actions/
│   ├── settings/
│   └── networklog/
├── domain/                     # use-cases, business logic
│   ├── capture/
│   ├── briefing/
│   ├── memory/
│   └── intent/                 # IntentRouter
├── data/
│   ├── db/                     # Room + SQLCipher
│   ├── llm/                    # BYOK provider abstraction
│   ├── calendar/               # Google + MS Graph
│   └── stt/                    # whisper.cpp JNI wrapper
├── service/
│   └── MamYListener.kt         # Foreground service
├── di/                         # Hilt modules
└── MainApplication.kt
```

### C. Risques techniques connus

| # | Risque | Mitigation |
|---|---|---|
| R1 | Battery drain wake-word continu | Profile sur 5+ devices, optimiser, monitoring opt-in |
| R2 | False-positive « MamY » en réunion (« papa », « ma mie »...) | Sensibilité user-tunable, fallback bouton, V2 confirmation tactile |
| R3 | Whisper-tiny FR jargon manager québécois faible | Test corpus réel, fallback whisper-base ou cloud STT |
| R4 | Foreground service tué par OEM custom (Samsung, Xiaomi battery saver) | Onboarding détecte + guide user pour whitelist app |
| R5 | LLM JSON parsing échoue | Fallback `non_structured=true`, user peut éditer manuellement |
| R6 | Calendar OAuth expire / révoqué | Detection + re-auth flow dans onboarding |
| R7 | Picovoice cost à scale | Monitor + bascule OpenWakeWord V2 |
| R8 | Homonyme mismatch (2 « Marie ») | TTS clarification + UI manuel |
