# Mamy — Design WIP

**Date** : 2026-05-02
**Statut** : Brainstorm en cours, sections 1-2 validées par user, sections 3-7 à venir.
**Contexte** : Idée standalone (pas dans la suite cyber existante). Brainstorm via skill `superpowers:brainstorming`.

---

## Décisions prises (verrouillées par user)

| # | Décision | Choix |
|---|---|---|
| D1 | **Cible** | Managers d'équipes de 30 à 100 employés |
| D2 | **Wedge V1** | Capture passive post-meeting (debrief vocal libre 60-90 sec) |
| D3 | **Mode capture** | Debrief vocal post-réunion (pas d'écoute live, pas de bot Zoom/Teams en V1) |
| D4 | **Plateforme V1** | Android-only (Kotlin natif). iOS = V2 plus tard. Raison : dev sur Windows, pas d'iPhone, doit pouvoir dogfooder. |
| D5 | **Stack technique** | Kotlin natif Android (vs Flutter/RN/Python). Raison : wake-word always-on + capture audio fiable, et iOS demandera Swift natif de toute façon. |
| D6 | **AI / Privacy** | BYOK (Claude/GPT/Gemini) avec mode hybrid par défaut (Whisper local + LLM cloud). Mode full-local opt-in. Audio jamais cloud. |
| D7 | **Always-on listening** | Foreground service léger, wake-word "Mamy" (Picovoice Porcupine custom model). |
| D8 | **Briefing flow** | Combo briefing matinal + notif silencieuse 5 min avant 1:1 + briefing vocal sur demande. Tout ajustable en settings. |

---

## Section 1 — Pitch + journée type ✅ VALIDÉ

### Pitch
> App Android always-on qui transforme tes 60-90 sec de debrief vocal post-1:1 en mémoire vivante par employé, te briefe avant chaque rencontre, et traque les promesses qui dérapent — pour managers de 30 à 100 personnes.

### Journée type — Marc, directeur de département, 45 reports

**8h00 — Café à la maison.**
Marc dit : « *Mamy, ma journée* » → l'app lit en vocal :
> « 5 1:1s aujourd'hui. Marie 10h — stressée projet X la dernière fois, tu devais relancer RH, c'est fait depuis. Pierre 11h30 — il t'avait promis le mockup vendredi passé, pas reçu. Julie 14h — première fois, nouveau report. Luc 15h — rien d'urgent. Anaïs 16h — elle attend ton feedback sur sa proposition. »

**9h55 — En route vers salle de réu (Marie 1:1).**
Vibration silencieuse. Marc dit : « *Mamy, briefe* » → 20 sec vocal :
> « Marie, dernier 1:1 il y a 8 jours, projet X la stresse parce que livrable Friday. RH t'a confirmé hier que sa demande de transfert interne est en cours. Promesses : tu lui as promis 30 min cette semaine pour reviewer son CV interne — c'est aujourd'hui. »

**10h45 — Sortie 1:1 avec Marie, Marc marche vers son bureau.**
« *Mamy, prends note. Marie va mieux, RH a confirmé son transfert pour juin, on a parlé du CV ensemble, elle veut postuler interne pour le rôle de lead. Faut que je parle à David du dept produit pour vérifier si ça matche son ouverture. Aussi, elle m'a dit que Pierre traîne sur le mockup parce qu'il est démotivé.* »

→ L'app structure :
- **Marie** → état : OK / promesses : aucune nouvelle / contexte : transfert juin confirmé, postule lead interne
- **Action générée** : « Parler à David (dept produit) du profil de Marie pour rôle lead »
- **Pierre** → flag rouge : démotivation détectée (source : Marie)
- **Promesse de Pierre** (mockup vendredi) → marquée *à creuser au 11h30 1:1*

**11h25 — Vibration. Briefing pré-Pierre :**
> « Pierre, mockup promis vendredi passé, pas reçu. Source indirecte : Marie t'a dit ce matin qu'il est démotivé. À creuser doucement. »

**18h00 — Fin de journée, retour maison.**
« *Mamy, résume* » → vocal :
> « 5 1:1s, 7 actions générées dont 2 ouvertes (parler David, suivi Pierre démotivation). 1 promesse passée à toi par Anaïs tenue. 1 promesse de toi à Marc Industries (le client) due demain. Les autres deals en cours — RAS. »

---

## Section 2 — Architecture technique ✅ VALIDÉ

### Stack
Kotlin natif, Jetpack Compose pour UI, target Android 9+ (API 28+, ~95 % des devices actifs).

### Composants principaux

```
┌─────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                         │
│  • Liste personnes • Détail personne • Briefing     │
│  • Settings (BYOK, briefing prefs, mode privacy)    │
└─────────────────────────────────────────────────────┘
                        ↓↑
┌─────────────────────────────────────────────────────┐
│  Foreground Service "MamyListener"                  │
│  (notif permanente, tourne 24/7, ~3-5 % CPU continu)│
│  ┌────────────────────────────────────────────┐     │
│  │ Wake-word engine (Picovoice Porcupine)     │     │
│  │ • Modèle "Mamy" custom (~100 KB, on-device)│     │
│  │ • Toujours en écoute, low CPU              │     │
│  └────────────────────────────────────────────┘     │
│         ↓ (wake-word fired)                         │
│  ┌────────────────────────────────────────────┐     │
│  │ Audio Recorder + VAD                       │     │
│  │ • AudioRecord 16 kHz mono                  │     │
│  │ • Silence detection → coupe l'enreg.       │     │
│  └────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│  STT local : whisper.cpp Android (whisper-tiny ~75MB)│
│  → texte brut                                       │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│  Structuration LLM (cloud, BYOK)                    │
│  • Prompt engineering : extrait person, état, action│
│    promesse, contexte                                │
│  • Provider configurable : Claude / GPT / Gemini    │
│  • Mode full-local optionnel : Phi-3 mini on-device │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│  Storage local : Room + SQLCipher (DB encryptée)    │
│  Tables : Person, Note, Action, Promise, Briefing   │
│  • Audio brut → JAMAIS stocké (default), opt-in     │
│  • Texte structuré → DB encryptée, master key       │
│    Android Keystore                                 │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│  Briefing Engine (WorkManager background tasks)     │
│  • Lit Google Calendar / Microsoft Graph (OAuth)    │
│  • Match meeting → Person dans DB                   │
│  • 5 min avant event → notif silencieuse + briefing │
│  • Briefing matinal 8h (heure ajustable)            │
│  • TTS Android natif pour lecture vocale (gratuit)  │
└─────────────────────────────────────────────────────┘
```

### Data flow d'un debrief vocal

1. User : « *Mamy, prends note...* » → Porcupine fire (latence ~200 ms)
2. Audio capture jusqu'à silence 1.5 sec (VAD)
3. Audio → Whisper local → texte brut (~1-2 sec pour 60 sec audio sur tel récent)
4. Texte → prompt structuré → LLM cloud BYOK :
   ```
   Extract from this manager debrief:
   - persons mentioned (with role/state)
   - actions to take (assignee, deadline)
   - promises tracked (who promised what to whom)
   - emotional context flags
   Return JSON.
   ```
5. JSON parsé → Room DB encryptée
6. Confirmation vocale courte : « *Noté. 2 actions, 1 personne flaggée.* »

### Permissions Android
- `RECORD_AUDIO` (always-on micro)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`
- `POST_NOTIFICATIONS`
- Calendar : `READ_CALENDAR` (system) OU OAuth Google/Microsoft Graph (cloud calendar)
- `INTERNET` pour BYOK API calls
- `WAKE_LOCK` (light) pour briefing matinal

### Footprint
- App size : ~50 MB binary + ~75 MB Whisper-tiny + ~100 KB Porcupine = **~125 MB total**
- RAM idle : ~80-120 MB (foreground service)
- Battery : ~5-8 %/jour (wake-word continu sur tel moderne, mesuré sur Pixel 6 ref)

### Note Porcupine
Picovoice Porcupine = leader sur les wake-words custom, mais commercial → free tier user-limit, payant à scale. Alternative open-source = OpenWakeWord ou Vosk (qualité un cran sous), gratuites mais demandent training custom du wake-word « Mamy » nous-mêmes. À trancher en V2 si Porcupine devient cher.

---

## Sections à venir

- **Section 3** — Capture & structuration (wake-word details, Whisper config, prompt engineering pour extraction)
- **Section 4** — Mémoire vivante & briefing flow (schéma DB, calendar sync, génération de briefing)
- **Section 5** — Privacy, BYOK, data flow détaillé
- **Section 6** — MVP scope vs V2
- **Section 7** — Naming, langue (FR/EN), modèle business, géo cible

---

## Questions ouvertes (à valider plus tard)

- Nom commercial du produit (« Mamy » = juste le wake-word)
- Langue UI V1 : FR seul ? FR + EN ?
- Marché géo V1 : Québec / Canada / North America / global
- Modèle business : freemium / trial 14j / subscription seule. Prix cible.
- Calendrier intégré V1 : Google + Outlook + Apple, ou un seul ?
- Provider LLM par défaut : Claude (matche écosystème user) vs Anthropic free-tier impossible donc OpenAI ?
- Mode Apple Watch / wearable companion (V3+ ?)
