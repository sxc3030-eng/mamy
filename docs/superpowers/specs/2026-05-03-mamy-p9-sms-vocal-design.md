# MamY P9 — SMS Vocal Feature Design Spec

**Date** : 2026-05-03
**Statut** : Design validé sections 1-8, prêt pour writing-plans.
**Auteur** : Brainstorm collaboratif via skill `superpowers:brainstorming` (suite du brainstorm 2026-05-02 qui a produit P1-P8).
**Repo** : `D:\ComfyUI-Intel\mamy\` · GitHub privé `sxc3030-eng/mamy` · branche `main` post-P6 (HEAD `23ea1f5`).
**Hub de suivi** : `D:\mamy\` (README + STATUS + ROADMAP + ORIGINAL_VISION).

---

## 1. Vision & pitch

### Pitch (1 phrase)
> Étendre MamY V1 avec une commande vocale **« MamY texte à \<X\> que \<message\> »** qui trouve le contact, extrait le message, demande confirmation vocale, puis envoie le SMS direct via `SmsManager` — full hands-free, eyes-free, sans toucher le téléphone.

### Pourquoi cette feature existe
- Le wedge MamY original (P1-P6) couvre la **mémoire managériale** (debrief 1:1, briefings, promesses bilatérales). Mais le manager passe aussi son temps à **texter** ses reports, son patron, ses clients — souvent pendant qu'il marche, conduit, ou a les mains occupées.
- L'expérience SMS via Siri/Google Assistant est **fragmentée et lente** : « Hey Siri, texte à Jimmy… → ouvrir Messages → tape send ». Pas full hands-free, pas intégré à la mémoire MamY.
- MamY a déjà tous les ingrédients (wake-word, STT local, LLM, TTS, person matching, homonyme clarifier). **Réutiliser pour SMS = effort marginal pour gros gain UX.**

### Wedge V1 SMS
**Une seule commande, un seul flow** : `text_to` intent reconnu, recipient extrait, body extrait, confirm vocal, send via `SmsManager` direct. Pas WhatsApp, pas email, pas appel téléphonique, pas read incoming. Le minimum viable qui change vraiment l'expérience quotidienne du manager.

### Ce que P9 SMS n'est PAS (anti-scope)
- ❌ Read incoming SMS (besoin permission `READ_SMS` réservée app SMS par défaut Play Store)
- ❌ App SMS par défaut (massive complexity, jamais nécessaire pour le wedge)
- ❌ WhatsApp send/read (pas d'API publique pour read, deep-link send pas dans scope V1)
- ❌ Email send/read
- ❌ Appel téléphonique vocal
- ❌ MMS, RCS, Signal, Telegram
- ❌ Bulk SMS, scheduling, templates (V2)

---

## 2. Décisions verrouillées (additions D14-D21 au spec MamY original)

| # | Décision | Choix | Raison |
|---|---|---|---|
| D14 | Scope SMS V1 | Send-only via SmsManager direct | User picked A : full vocal hands-free |
| D15 | Drop scope autres canaux | WhatsApp + email + appel + read incoming = OUT | User : « *on oublie WhatsApp, read email* » |
| D16 | Mécanisme send | `SmsManager.sendTextMessage` (requires SEND_SMS permission) NOT `Intent.ACTION_SENDTO` | User picked A : priorité full vocal vs zéro permission |
| D17 | Distribution V1 | APK signé sideload via GitHub Releases (pas Play Store V1) | User : « app téléchargeable que je peux faire test à des gens » |
| D18 | Stratégie de phasage exécution | 2 waves de 5 agents parallèles (P7 + P9 SMS en wave 1, P8 + smoke en wave 2) | User picked B : parallel agents proven pattern |
| D19 | Confirmation vocale obligatoire avant envoi | OUI, default ON, désactivable en V2 | Sécurité — éviter SMS envoyé par erreur quand wake-word false-positive |
| D20 | Privacy mode STRICT pour SMS | Implémenté V1 (regex-only, no LLM fallback) | Coût marginal V1, killer feature pour user privacy-conscious |
| D21 | Privacy mode HYBRID + REDACTION (NER local) | V2 only (~10-13 j additionnels) | Killer feature enterprise V2, pas critical V1 |

---

## 3. Persona + journey type étendu

### Persona principale (rappel) : Marc Tremblay, 42 ans
Directeur d'opérations PME manufacturière 280 personnes à Drummondville. Manage 6 reports directs (chefs d'équipe), ~45 indirects. ~25 1:1s/mois. Drive 30 min entre 2 sites. Pixel 7 Android.

### Pain point spécifique adressé par P9
Marc passe sa journée à texter :
- « Jimmy, tu peux passer dans 10 min ? » (chef d'équipe production)
- « Marie, OK pour 14h » (RH)
- « Pierre, le rapport est-il prêt ? » (collègue)

Aujourd'hui il doit : sortir le tel → unlock → ouvrir Messages → chercher contact → taper le message → tap send. **Friction énorme**, surtout en voiture / en marchant / en réunion debout. Siri marche mais perd le contexte de sa mémoire managériale (qui est qui dans son équipe). Google Assistant pareil.

### Journey type avec MamY P9

**🚗 18h00 — Marc en voiture, mains sur volant, sortie d'usine**

Marc : « *MamY, texte à Jimmy que c'est bon pour ce soir* »

*(MamY pipeline P1-P6 existant : Porcupine fire → AudioCapture → Whisper STT → texte « texte à Jimmy que c'est bon pour ce soir »)*

*(NEW P9 : IntentRouter détecte pattern `text_to` regex → Intent.TextTo(who="Jimmy", body="c'est bon pour ce soir"))*

*(NEW P9 : ContactMatcher cascade → Person table P1 trouve Jimmy Tremblay (chef d'équipe production, calendar-matched par P5) avec phone +15145551234 (mobile))*

🤖 **MamY (TtsConfirmer P3, voix douce FR)** : « *Je texte à Jimmy Tremblay au 514-555-1234 : « C'est bon pour ce soir ». Confirmé ?* »

*(NEW P9 : VoiceConfirmListener via Android SpeechRecognizer, 3 sec timeout)*

Marc : « *oui* »

*(NEW P9 : SmsSender.send → SmsManager.sendTextMessage → SMS submit to radio. SentSmsEntry inserted dans Room SQLCipher avec status=`pending` puis `sent` après PendingIntent callback)*

🤖 **MamY** : « *Envoyé.* »

📱 **Optionnel post-send** : Marc dit « *MamY, qu'est-ce que je viens de texter* » → MamY relit la dernière SMS envoyée (V1.1 feature, intent `last_sms_recap`)

### Cas spéciaux

**Cas homonymes** (2+ « Jimmy » dans contacts ou Person table) :

🤖 « *Tu parles de Jimmy Tremblay ou Jimmy Lebrun ?* » → user dit nom → pick → continue le flow normal *(HomonymeClarifier P4 réutilisé)*

**Cas pas de match** :

🤖 « *Pas de Jimmy dans tes contacts.* » → cancel.

**Cas contact sans tel** :

🤖 « *Jimmy Tremblay n'a pas de numéro de téléphone.* » → cancel.

**Cas annulation** :

Marc : « *non* » ou « *annule* » → MamY abandonne, rien n'est envoyé, status=`cancelled` dans DB pour audit.

**Cas pas de réseau** :

`SmsManager` fail avec `RESULT_ERROR_NO_SERVICE` → status reste `pending`, TTS « *Pas de réseau, je réessaye plus tard* » (V2 auto-retry, V1 manual retry depuis l'UI).

**Cas permission SEND_SMS pas grantée** :

Première fois → trigger `PermissionLauncher` (P2 existing) → user accepts → retry pending. Si refuse → cancel + TTS « *Permission SMS refusée. Va dans Réglages → MamY → Permissions* ».

---

## 4. Architecture technique

### Stack (héritage P1-P6)
- Kotlin 2.0.21 + Jetpack Compose + Hilt 2.52
- Min SDK 28, Target SDK 35
- Package : `com.mamy.android`
- Tous les composants P1-P6 réutilisés (wake-word, STT, LLM, TTS, person matcher, homonyme clarifier, etc.)

### Composants nouveaux

```
┌─────────────────────────────────────────────────────────────┐
│  Voice : "MamY texte à Jimmy que c'est bon pour ce soir"    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│  P2 Pipeline (REUSE) : wake-word → AudioCapture →        │
│                        Whisper STT (P2 existing)         │
│  → "texte à Jimmy que c'est bon pour ce soir"            │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│  IntentRouter (EXTEND P4) : new pattern `text_to` regex  │
│  → Intent.TextTo(who="Jimmy", body="c'est bon ce soir")  │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│  IntentDispatcher (EXTEND P4) → TextToHandler (NEW)      │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│  ContactsRepository (NEW) — Android ContactsContract     │
│  + ContactMatcher (NEW) : cascade Person→exact→fuzzy     │
│  → MatchResult.Single(contact) | Multiple(list) | None   │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│  HomonymeClarifier (REUSE P4) → unique contact picked    │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│  TtsConfirmer (REUSE P3) : "Je texte à Jimmy au 514-…    │
│  : 'c'est bon ce soir'. Confirmé ?"                      │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│  VoiceConfirmListener (NEW) — Android SpeechRecognizer   │
│  listen-once 3 sec → "oui"|"non"|timeout                 │
└──────────────────────────────────────────────────────────┘
                ↓ (oui)              ↓ (non/timeout)
┌──────────────────────────┐  ┌──────────────────────────┐
│ SmsSender (NEW)          │  │ Cancel : status=cancelled │
│ SmsManager.sendTextMsg() │  │ TTS : "Annulé"            │
└──────────────────────────┘  └──────────────────────────┘
                ↓
┌──────────────────────────────────────────────────────────┐
│  SmsStatusReceiver (NEW) — BroadcastReceiver pour les    │
│  PendingIntents sent/delivered. Update SentSmsEntry status│
└──────────────────────────────────────────────────────────┘
                ↓
┌──────────────────────────────────────────────────────────┐
│  SentSmsDao (NEW) — Room insert + observe par Person     │
│  + TtsConfirmer "Envoyé"                                 │
└──────────────────────────────────────────────────────────┘
```

### Nouveaux packages

```
app/src/main/kotlin/com/mamy/android/
├── data/contacts/                         (NEW)
│   ├── ContactsRepository.kt              (Flow<List<Contact>> via ContentResolver)
│   ├── Contact.kt                         (data class : id, displayName, phones[], emails[])
│   ├── PhoneNumber.kt                     (E.164 + type)
│   ├── ContactMatcher.kt                  (cascade matcher : Person→exact→substring→fuzzy)
│   └── MatchResult.kt                     (sealed : Single | Multiple | None)
├── data/sms/                              (NEW)
│   ├── SmsSender.kt                       (SmsManager wrapper + permission check)
│   ├── SmsResult.kt                       (sealed : Sending | Sent | Failed | PermissionDenied | NoCarrier)
│   ├── SmsStatusReceiver.kt               (BroadcastReceiver pour PendingIntents)
│   └── VoiceConfirmListener.kt            (SpeechRecognizer wrapper, 3s timeout)
├── data/db/
│   ├── entity/SentSmsEntry.kt             (NEW Room entity)
│   └── dao/SentSmsDao.kt                  (NEW)
├── domain/intent/
│   ├── Intent.kt                          (EXTEND : add TextTo(who, body) case)
│   ├── IntentGrammar.kt                   (EXTEND : add text_to regex FR+EN)
│   └── handler/TextToHandler.kt           (NEW : full orchestrator)
└── di/
    ├── ContactsModule.kt                  (NEW)
    └── SmsModule.kt                       (NEW)
```

### Permissions Android

Ajouts au `AndroidManifest.xml` :

```xml
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.SEND_SMS" />
```

Granted au runtime via le `PermissionLauncher` existant (P2). User prompt une fois la première fois qu'il dit « MamY texte à... ».

### Schema DB additions

`MamYDatabase.kt` bumpé en version=3, nouvelle entité :

```kotlin
@Entity(tableName = "sent_sms_entry")
data class SentSmsEntry(
    @PrimaryKey val id: UUID,
    val recipientContactId: String?,        // Android ContactsContract _ID
    val recipientPersonId: UUID?,           // FK Person — si match avec ton équipe
    val recipientPhone: String,             // E.164 normalisé (+15145551234)
    val recipientDisplayName: String,
    val body: String,
    val sentAt: Instant,
    val status: String,                     // "pending"|"sent"|"delivered"|"failed"|"cancelled"
    val failReason: String?,
    val rawIntentText: String,              // STT brut, pour audit
    val segments: Int = 1,                  // 1 pour <160 chars, 2-3+ pour multi-segments
    val privacyMode: String,                // "standard"|"strict"|"hybrid_redaction"
)
```

Et `Person` entity étendue avec un champ :

```kotlin
@Entity Person(
    // ... champs existants P1+P5 ...
    val androidContactId: String?,           // NEW : link to ContactsContract _ID
)
```

DB version=3 avec `fallbackToDestructiveMigration()` (déjà activé P1) pour dev. V1.1 peut écrire une vraie migration si besoin.

### Footprint additionnel
- Code source : ~3 000 lignes Kotlin nouvelles (~12-15 fichiers)
- Tests : ~1 500 lignes (~30-40 nouveaux tests)
- DB additionnelle : 1 table `sent_sms_entry` + 1 colonne `Person.android_contact_id`
- APK size impact : ~+200 KB (pas de native lib supplémentaire, juste Kotlin)
- RAM impact idle : négligeable
- Battery : `ContentObserver` sur Contacts est cheap (event-based, pas polling)

---

## 5. Voice grammar (text_to intent)

### Regex primaire

```kotlin
// IntentGrammar.kt — append to existing patterns
private val TEXT_TO_FR = Regex(
    "^MamY,?\\s+(?:texte|envoie\\s+(?:un\\s+)?(?:texto|sms)|dis)\\s+" +
    "(?:à\\s+)?(?<who>[\\w\\-'\\u00C0-\\u017F]+(?:\\s+[\\w\\-'\\u00C0-\\u017F]+)?)" +
    "\\s+(?:que\\s+|dis(?:-?lui)?\\s+|:\\s*|,\\s*)" +
    "(?<body>.+)$",
    RegexOption.IGNORE_CASE
)

private val TEXT_TO_EN = Regex(
    "^MamY,?\\s+(?:text|send\\s+(?:a\\s+)?(?:text|sms)\\s+to)\\s+" +
    "(?:to\\s+)?(?<who>[\\w\\-']+(?:\\s+[\\w\\-']+)?)" +
    "\\s+(?:that\\s+|saying\\s+|:\\s*|,\\s*)" +
    "(?<body>.+)$",
    RegexOption.IGNORE_CASE
)
```

### Validation post-extraction

- `who` : 1-50 chars, ASCII + accents Latin Ext-A (`À-ſ`)
- `body` : ≥3 chars, ≤320 chars (cap à 2 segments SMS)
- Si invalide → bascule LLM fallback (sauf en mode STRICT)

### LLM fallback (si regex échoue, mode STANDARD ou HYBRID_REDACTION)

System prompt court (~50 tokens, ~$0.0001 per call) :

```
Extract from this French/English voice command : the recipient's name and 
the message body to send. Return JSON exactly:
{"who": "<name>", "body": "<message>"}
Or {"who": null, "body": null} if it's not a SMS-send command.

Voice command: «{transcript}»
```

LLM appelé seulement si regex miss. ~5-10 % des cas en pratique.

### Phrases test (corpus TDD)

| Voice input | Match | who | body |
|---|---|---|---|
| « MamY texte à Jimmy que c'est bon pour ce soir » | ✅ regex FR | Jimmy | c'est bon pour ce soir |
| « MamY texte Jimmy : c'est bon pour ce soir » | ✅ regex FR | Jimmy | c'est bon pour ce soir |
| « MamY envoie un texto à Marie Dubois que je serai en retard » | ✅ regex FR | Marie Dubois | je serai en retard |
| « MamY dis à Pierre que la réunion est déplacée » | ✅ regex FR | Pierre | la réunion est déplacée |
| « MamY text Jimmy that I'm running late » | ✅ regex EN | Jimmy | I'm running late |
| « MamY send Marie a text saying meeting at 3 » | ✅ regex EN | Marie | meeting at 3 |
| « MamY ehhh, peux-tu texter Jimmy pour lui dire que c'est ok » | ❌ regex → ✅ LLM | Jimmy | c'est ok |
| « MamY texte Jean-François » (no body) | ✅ regex but body empty → trigger « *Quel message ?* » re-prompt (V1.1) ; V1 = cancel |
| « MamY » (just wake-word, no text intent) | n/a — fallback to capture intent |

### Confirmation vocale grammar

```kotlin
// VoiceConfirmListener — match against SpeechRecognizer result string

private val CONFIRM = Regex(
    "^\\s*(oui|yes|ok|envoie|envoyer|confirm[éée]?|vas-?y|go)\\s*$",
    RegexOption.IGNORE_CASE
)
private val CANCEL = Regex(
    "^\\s*(non|no|annule(?:r)?|stop|arr[êe]te|cancel)\\s*$",
    RegexOption.IGNORE_CASE
)
// Anything else OR 3-second silence → defaults to CANCEL (safety)
```

---

## 6. Contacts integration + matching strategy

### ContactsRepository (lecture Android natif)

```kotlin
// data/contacts/ContactsRepository.kt
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Reactive Flow<List<Contact>>. Re-emits on ContactsContract changes. */
    fun observeContacts(): Flow<List<Contact>> = callbackFlow {
        if (!hasContactsPermission()) {
            trySend(emptyList()); awaitClose {}; return@callbackFlow
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(self: Boolean) { trySend(loadContacts()) }
        }
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI, true, observer
        )
        trySend(loadContacts())
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private fun loadContacts(): List<Contact> {
        // ContentResolver.query loops over Contacts + CommonDataKinds.Phone + Email
        // Returns List<Contact> with displayName, firstName, lastName, phones[], emails[]
    }
}

data class Contact(
    val id: String,                // Android ContactsContract _ID
    val displayName: String,       // "Jimmy Tremblay"
    val firstName: String?,        // "Jimmy" (parsed)
    val lastName: String?,         // "Tremblay"
    val phones: List<PhoneNumber>, // E.164 + type (mobile/home/work)
    val emails: List<String>,
)

data class PhoneNumber(
    val e164: String,              // "+15145551234"
    val type: PhoneType,           // MOBILE | HOME | WORK | OTHER
)
```

### ContactMatcher (cascade)

```
1. PERSON exact match (P1 Person table — ton équipe matchée Calendar P5)
   → priorité absolue (tes reports passent avant random Jimmy de tes contacts)
   ↓ si miss
2. CONTACT exact match case-insensitive + accent-normalized
   ↓ si miss
3. CONTACT substring match
   ↓ si miss
4. CONTACT Levenshtein fuzzy (distance ≤ 2)
   ↓ si miss
5. MatchResult.None → TTS "Pas de <X> dans tes contacts"
```

```kotlin
class ContactMatcher @Inject constructor(
    private val personDao: PersonDao,
    private val contactsRepo: ContactsRepository,
) {
    suspend fun findByName(query: String): MatchResult { /* implementation */ }
}

sealed class MatchResult {
    data class Single(val contact: Contact, val fromTeam: Boolean = false) : MatchResult()
    data class Multiple(val contacts: List<Contact>) : MatchResult()
    data object None : MatchResult()
}
```

### Phone selection priority

```
MOBILE > WORK > HOME > OTHER, pick first
Si pas de phone → MatchResult traité comme None
Si 2+ MOBILE → pick le premier (V1) ou ask user (V2)
```

### Person ↔ Contact bridging

P5 a déjà `Person.calendar_attendee_id` (email du calendrier). On ajoute `Person.android_contact_id`.

**Auto-link strategy** : background sync, idempotent, déclenché à chaque émission de ContactsRepository :
- Pour chaque Person avec `calendar_attendee_id` non null
- Cherche Contact avec `email == calendar_attendee_id`
- Si match unique → set `Person.android_contact_id`

Avantage : « MamY texte à Marie » dit pendant un 1:1 → trouve Marie (ton report) instantanément, pas une autre Marie aléatoire dans tes contacts.

### Performance

- **Cache** : ContactsRepository garde un `StateFlow<List<Contact>>` chaud (1 query au boot, refresh sur ContentObserver)
- Volume typique : ~500-2000 contacts → query <100 ms, in-memory match O(N) <10 ms
- Pas de Room cache pour contacts (ContentResolver est canonique)

### Privacy contacts

- **Contacts restent local 100 %** : jamais cloud, même pas le nom
- Le LLM fallback reçoit le **transcript STT** (qui peut contenir le nom prononcé), pas la liste des contacts
- Le matching réel se fait **on-device** après retour LLM
- DB encrypted SQLCipher (P1) protège `Person.android_contact_id` link
- RGPD/Loi 25 : pas de data leak supplémentaire

---

## 7. SMS send mechanism + privacy + error handling

### SmsSender (wrapper SmsManager)

```kotlin
@Singleton
class SmsSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sentSmsDao: SentSmsDao,
    private val clock: Clock,
) {
    suspend fun send(
        recipient: Contact,
        phoneE164: String,
        body: String,
        rawIntentText: String,
        linkedPersonId: UUID? = null,
        privacyMode: PrivacyMode,
    ): SmsResult {
        if (!hasSendSmsPermission()) return SmsResult.PermissionDenied
        
        val entry = SentSmsEntry(/* ... */)
        sentSmsDao.insert(entry) // status="pending"
        
        val sentIntent = makeSentPI(entry.id)
        val deliveredIntent = makeDeliveredPI(entry.id)
        
        return runCatching {
            val sms = context.getSystemService(SmsManager::class.java)
            val parts = sms.divideMessage(body)
            if (parts.size == 1) {
                sms.sendTextMessage(phoneE164, null, body, sentIntent, deliveredIntent)
            } else {
                sms.sendMultipartTextMessage(/* ... */)
            }
            SmsResult.Sending(entry.id, parts.size)
        }.getOrElse { t ->
            sentSmsDao.updateStatus(entry.id, "failed", t.message)
            SmsResult.Failed(t.message ?: "unknown")
        }
    }
}

sealed class SmsResult {
    data class Sending(val entryId: UUID, val segments: Int) : SmsResult()
    data class Sent(val entryId: UUID) : SmsResult()
    data class Delivered(val entryId: UUID) : SmsResult()  // V2 only displayed
    data class Failed(val reason: String) : SmsResult()
    data object PermissionDenied : SmsResult()
    data object NoCarrier : SmsResult()
}
```

### Status callbacks via BroadcastReceiver

```kotlin
class SmsStatusReceiver : BroadcastReceiver() {
    @Inject lateinit var sentSmsDao: SentSmsDao
    
    override fun onReceive(ctx: Context, intent: Intent) {
        val entryId = UUID.fromString(intent.getStringExtra(EXTRA_ENTRY_ID))
        when (intent.action) {
            ACTION_SMS_SENT -> when (resultCode) {
                Activity.RESULT_OK -> sentSmsDao.updateStatus(entryId, "sent", null)
                else -> sentSmsDao.updateStatus(entryId, "failed", reasonFromCode(resultCode))
            }
            ACTION_SMS_DELIVERED -> sentSmsDao.updateStatus(entryId, "delivered", null)
        }
    }
}
```

Registered dynamiquement dans `MamYListenerService.onCreate()` (scope process-only, pas dans manifest).

### Permission flow (SEND_SMS first-use)

```
User : "MamY texte à Jimmy que c'est bon"
  ↓
TextToHandler invoked
  ↓
SmsSender.send() → return PermissionDenied
  ↓
TextToHandler stores intent in `pending_send` (in-memory) + asks user via TTS:
  "L'envoi de SMS a besoin d'une permission. Touche l'app pour l'activer."
  ↓
PermissionLauncher (P2 existing) triggered
  ↓
User accepts
  ↓
TextToHandler retries pending_send → SmsSender.send() → SmsResult.Sending
```

Si user refuse : TTS « *Permission SMS refusée. Ouvre Réglages → MamY → Permissions → SMS pour activer.* » + cancelled status.

### Error handling table complète

| Erreur | Detection | Action user-facing |
|---|---|---|
| Permission `SEND_SMS` refused (1ère fois) | `checkSelfPermission` | TTS « *Permission SMS nécessaire — touche l'app* » + launch permission |
| Permission refused « Don't ask again » | `shouldShowRationale=false` | TTS « *Va dans Réglages > MamY > Permissions* » + cancel |
| Permission `READ_CONTACTS` refused | idem | TTS « *Permission contacts nécessaire* » + cancel |
| Pas de SIM / mode avion | `SmsManager.RESULT_ERROR_NO_SERVICE` | TTS « *Pas de réseau cellulaire* », status `pending`, retry queue (V2) |
| Network busy / radio off | `RESULT_ERROR_RADIO_OFF` | TTS « *Réseau non dispo, je réessaye dans 30 sec* » + WorkManager retry × 3 (V1.1) |
| Numéro invalide | `RESULT_ERROR_NULL_PDU` | TTS « *Le numéro semble invalide* » + cancel |
| Contact supprimé entre lookup et send | `Contact.id` orphelin au lookup | TTS « *Le contact n'existe plus* », cancel |
| Body trop long (>320 chars / >2 segments) | `divideMessage().size > 2` | TTS « *Message trop long, 3 SMS, confirmé ?* » re-confirm avant envoi (V1.1) |
| LLM fallback timeout (5s) | OkHttp call timeout | TTS « *Cloud LLM timeout, peux-tu reformuler ?* » + cancel |
| TTS pas dispo (locale FR pas installée) | `TextToSpeech.LANG_NOT_SUPPORTED` | Fallback toast écran + Snackbar + log |
| Whisper transcrit mal (« texte à Tchimmy ») | Levenshtein matche `Jimmy` distance 2 | Pris en compte par fuzzy matcher |
| User dit « oui » mais background noise → SpeechRecognizer fail | `onError` callback | TTS « *Pas compris, je réessaye une fois* » → second listen → si encore fail, cancel |
| Doppelgangers homonymes | `MatchResult.Multiple` | HomonymeClarifier P4 réutilisé |

### Retry queue

**V1** : status `pending` reste dans DB. Liste visible dans UI. User peut tap « retry » manuel. Pas d'auto-retry V1 (évite spamming réseau).

**V1.1** : `WorkManager` `SmsRetryWorker` (pareil pattern que `CalendarSyncWorker` P5) : retry automatique exponential backoff sur les `pending` quand connectivity revient.

### Multi-segment SMS

SmsManager facture par segment :
- Single 1-160 chars (GSM-7) ou 1-70 chars (UCS-2 si emoji/accent)
- Plus long = split auto en 2-3 segments

V1 : pas de display cost. V2 : log dans NetworkLog (« 1 SMS sent à 514-... »).

---

## 8. UI : history + settings + onboarding

### PersonDetailScreen (extend P7 plan)

Nouveau **tab « SMS »** dans la nav existante :

```
┌─ Marie Tremblay ─────────────────────────────────┐
│  📊 emotional_trend · 🔴 1 flag                  │
│  ┌─────┬──────────┬─────────┬───────────────┐    │
│  │Notes│ Promises │ Actions │ SMS (3)        │   │  ← NEW
│  └─────┴──────────┴─────────┴───────────────┘    │
│                                                  │
│  ── SMS envoyés via MamY ─────────────────       │
│  📤 « C'est bon pour ce soir »                   │
│      ✅ Envoyé · il y a 12 min · 514-555-1234    │
│  ────────────────────────────────────────────    │
│  📤 « Réunion déplacée à 14h »                   │
│      ⏳ En attente · pas de réseau               │
│      [ Réessayer ]                               │
│  ────────────────────────────────────────────    │
│  📤 « Bon weekend ! »                            │
│      ❌ Échec · numéro invalide                  │
│      [ Voir détails ]                            │
└──────────────────────────────────────────────────┘
```

Status badges :
- ⏳ pending (gris) → tap « Retry » → `SmsSender.retry(entryId)`
- ✅ sent (vert)
- ✅✅ delivered (vert+bold) — V2
- ❌ failed (rouge) → tap « Voir détails » → modal avec `fail_reason`
- ⛔ cancelled (gris barré)

### SettingsScreen (extend P7 plan)

Nouvelle section dans le SettingsScreen modulaire :

```
┌─ Settings ─────────────────────────────────────┐
│  ⚙️ Général                                    │
│  📲 SMS  ←────────────────────────────── NEW   │
│      ┌──────────────────────────────────────┐  │
│      │ Activer commandes SMS         [ON ]  │  │
│      │ Permission SEND_SMS           ✅      │  │
│      │ Permission READ_CONTACTS      ✅      │  │
│      │ Confirmation vocale obligatoire [ON] │  │
│      │ ╭──────────────────────────────────╮ │  │
│      │ │ Mode privacy SMS                  │ │  │
│      │ │ ○ Standard (LLM fallback OK)      │ │  │
│      │ │ ● Strict (regex only, no cloud)   │ │  │  ← V1
│      │ │ ○ Hybrid+redaction NER local [V2] │ │  │  ← V2
│      │ ╰──────────────────────────────────╯ │  │
│      │ Auto-retry pending SMS [V1.1]        │  │
│      │ Voir audit log SMS              >    │  │  → NetworkLog filtered
│      └──────────────────────────────────────┘  │
│  💰 BYOK & Cost                                │
│  🔒 Privacy                                    │
│  📅 Calendar                                   │
│  ...                                           │
└────────────────────────────────────────────────┘
```

### OnboardingScreen step 4/6 (extend P7 plan)

```
┌─ Onboarding step 4/6 ──────────────────────────┐
│  📲                                            │
│                                                │
│   Envoie tes SMS à la voix                     │
│                                                │
│   « MamY texte à Jimmy que c'est bon »         │
│                                                │
│   Permissions nécessaires :                    │
│   • Lire tes contacts (pour trouver Jimmy)     │
│   • Envoyer des SMS                            │
│                                                │
│   [ Activer ]      [ Plus tard ]               │
└────────────────────────────────────────────────┘
```

« Plus tard » → app marche sans SMS, user peut activer plus tard via Settings.

### DataScreen — historique global SMS

Section ajoutée à DataScreen (P7 plan) :

```
┌─ Tes données ─────────────────────────────────┐
│  📊 Statistiques                              │
│  ├─ 47 personnes                              │
│  ├─ 192 notes                                 │
│  ├─ 28 actions ouvertes                       │
│  └─ 156 SMS envoyés via MamY  ←── NEW         │
│                                               │
│  📤 Historique SMS  ←── NEW                   │
│  [ Voir tous les SMS envoyés ]                │
│                                               │
│  💾 Export                                    │
│  [ Exporter tout en JSONL chiffré ]           │
│                                               │
│  🗑️ Effacer                                   │
│  [ Effacer une personne ]                     │
│  [ Effacer tout ]                             │
└───────────────────────────────────────────────┘
```

### VoiceIndicator overlay

Reuse P7 plan, ajout 1 état :
- **« 📲 Confirmation SMS »** (orange pulsing) pendant les 3 sec où MamY attend ton « oui/non »

### Strings i18n à ajouter (FR + EN)

~25 nouvelles clés dans `values/strings.xml` + `values-fr/strings.xml` (liste complète en annexe).

---

## 9. Privacy modes spécifiques SMS

### 3 modes (héritage P3 SettingsRepository.PrivacyMode)

| Mode | Recipient extraction | Body extraction | Cloud transmission |
|---|---|---|---|
| **STANDARD** (default) | Regex local primaire ; LLM fallback voit transcript COMPLET (recipient + body) | idem | Texte transcript → cloud BYOK chez le provider |
| **STRICT** (V1, opt-in) | Regex local UNIQUEMENT. Si miss → TTS « *Reformule simplement, je n'ai pas compris* » | idem | **Aucune transmission cloud, ever**. Pas de LLM fallback. |
| **HYBRID + REDACTION** (V2 enterprise) | NER local extrait noms propres → remplace par `PERSON_42` AVANT envoi cloud | NER strip noms + emails + tél + dates → `MEETING_1`, `EMAIL_1` | Texte anonymisé → LLM voit jamais les vrais noms ; app remappe au retour |

### Mode STRICT (V1 implementation, ~0 jour additionnel)

```kotlin
class TextToHandler @Inject constructor(
    private val grammar: IntentGrammar,
    private val llmStructurer: LlmStructurer,
    private val privacyModeFlow: Flow<PrivacyMode>,
    /* ... */
) {
    suspend fun handle(transcript: String): TextToResult {
        val regexMatch = grammar.matchTextTo(transcript)
        if (regexMatch != null) return regexMatch.toResult()
        
        val mode = privacyModeFlow.first()
        if (mode == PrivacyMode.STRICT) {
            ttsConfirmer.speak("Reformule simplement, je n'ai pas compris.")
            return TextToResult.Cancelled(reason = "strict_mode_no_llm")
        }
        
        return llmStructurer.extractTextTo(transcript).toResult()
    }
}
```

### Mode HYBRID + REDACTION (V2, ~10-13 j)

**Architecture** :

```
Transcript STT : "texte à Jimmy Tremblay que la réunion à 14h est confirmée chez Acme"
       ↓
NerLocal (ONNX ~8 MB ou TFLite, MobileBERT-tinyNER fine-tuned FR+EN)
       ↓
Entities found : PERSON("Jimmy Tremblay"), TIME("14h"), ORG("Acme")
       ↓
Anonymisation : "texte à PERSON_1 que la réunion à TIME_1 est confirmée chez ORG_1"
       ↓
LLM cloud BYOK voit ÇA seulement
       ↓
LLM return : {who: "PERSON_1", body: "la réunion à TIME_1 est confirmée chez ORG_1"}
       ↓
EntityRemapper : remplace placeholders par valeurs originales
       ↓
who = "Jimmy Tremblay", body = "la réunion à 14h est confirmée chez Acme"
```

**Pitch enterprise** :
> « Vos employés et clients ne quittent jamais le téléphone en clair. MamY anonymise localement chaque transcript avant tout appel cloud. »

### Audit log SMS (réutilise NetworkLog P5)

Quand le LLM fallback est invoqué pour extraction SMS :

```kotlin
NetworkLogEntry(
    type = "llm_extract_sms",
    provider = "claude" | "openai" | "gemini",
    timestamp = Instant.now(),
    bytes_sent = transcript.length,
    bytes_received = response.length,
    cost_microcents = 250,
    redacted = false,                                        // V1 STANDARD ; V2 redacted=true si HYBRID
    raw_transcript_preview = transcript.take(50) + "...",    // truncated for privacy in log
)
```

User voit dans `NetworkLogScreen` tous les appels LLM SMS-related → transparence totale.

---

## 10. MVP scope V1 / V1.1 / V2 / V3

### V1 SMS feature (ship dans l'APK sideload, ~5-7 j dev avec agents parallèles)

**Voice & Intent** :
- ✅ Voice grammar `text_to` regex FR + EN
- ✅ LLM fallback extraction (mode STANDARD)
- ✅ Confirmation vocale obligatoire avant envoi

**Contacts** :
- ✅ ContactsRepository read-only via `ContactsContract`
- ✅ ContactMatcher cascade (Person team → exact → substring → Levenshtein fuzzy ≤2)
- ✅ Phone selection MOBILE > WORK > HOME > OTHER, pick first
- ✅ HomonymeClarifier réutilisé (P4) pour multi-matches

**SMS** :
- ✅ SmsSender via `SmsManager.sendTextMessage` direct (full vocal)
- ✅ Status callbacks `sent` / `failed` via PendingIntent broadcasts
- ✅ SentSmsEntry Room table + DAO + DB version=3
- ✅ Permission flow runtime READ_CONTACTS + SEND_SMS

**UI** :
- ✅ PersonDetail SMS tab (liste paginée 50 derniers SMS, status badges)
- ✅ Settings SMS section (master toggle, permission display, mode privacy, mode strict)
- ✅ Onboarding step 4/6 « Activer SMS vocaux » avec skip
- ✅ DataScreen historique global + total count
- ✅ VoiceIndicator nouvel état "Awaiting SMS confirm"
- ✅ i18n FR + EN (~25 nouvelles strings)

**Error handling complet** : 12 cas listés Section 5

### V1.1 (~1 sem post-V1)

- Auto-retry pending SMS via `WorkManager` (`SmsRetryWorker`)
- Multi-segment confirmation : « *2 SMS, OK ?* » re-confirm
- Re-prompt body missing : « *Quel message ?* » + listen 5s
- « MamY qu'est-ce que je viens de texter » → relit dernière SMS (intent `last_sms_recap`)
- Edit before send : « *non, change le message en...* » → reformat + re-confirm

### V2 (~3-6 mois post-V1, post-traction)

- Delivered status display (V1 affiche juste `sent`)
- Multi-phone disambiguation : « *Au mobile ou au fixe ?* »
- Cost display per segment SMS
- Search SMS history dans DataScreen
- « MamY transfère ce SMS à Jimmy » forwarding
- Bulk send : « *MamY texte à toute mon équipe...* »
- SMS scheduling : « *MamY texte à Jimmy demain à 9h...* »
- Templates persistants : « *MamY envoie mon rappel hebdo à Marie* »
- **HYBRID + REDACTION mode** (NER local) — killer feature enterprise

### V3 / Enterprise

- Compliance audit log signé HMAC chain
- Centralized BYOK (Picovoice + LLM) admin org
- MDM deployment Intune / JAMF
- Self-hosted endpoint LLM extraction

### Cutting list (NEVER)

- ❌ WhatsApp send/read (user décision)
- ❌ Email send/read
- ❌ Phone call vocal
- ❌ Read incoming SMS
- ❌ MMS, RCS, Signal, Telegram
- ❌ App SMS par défaut mode

### Smallest possible V1 (si urgence ship 3 j au lieu de 7)

Cuts si nécessaire :
- ❌ Onboarding step 4/6 → permission demandée à la 1ère commande SMS
- ❌ Settings SMS section → master toggle uniquement
- ❌ DataScreen historique global → juste tab PersonDetail
- ❌ VoiceIndicator state SMS → reuse "processing" générique
- ❌ Multi-segment auto-confirm → split silently
- ❌ LLM fallback extraction → regex only ; si miss → TTS « *Pas compris, reformule* »
- ❌ Status callbacks → fire & forget
- 5 voice grammar variants au lieu de 8

Recommandé **PAS** ce cut : écart 3 j vs 7 j est petit, UX poli est ce qui rend l'app vendable.

---

## 11. Métriques de succès

### V1 SMS (alpha-beta)

- **Tu peux envoyer un SMS sans toucher ton tel** (test live sur ton tel après install)
- **5/5 commandes vocales typiques marchent** (test corpus Section 5)
- **0 SMS envoyé par erreur** (confirmation vocale obligatoire active toujours)
- **Permission flow comprhensible** (testeur lambda configure en <2 min)
- **Cas homonymes gérés** : 2+ « Jimmy » → user picks vocal, pas confusion
- **DB encrypted** : `mamy.db` lu avec sqlite3 → gibberish

### V1 ship public (3 mois post-launch)

- ≥ 5 SMS vocaux envoyés/jour par tester actif (proxy d'usage SMS feature)
- < 1 % SMS envoyé par erreur (vraies négatives confirmation)
- < 5 % churn mensuel sur la cohorte qui a activé SMS feature

---

## 12. Open questions (à résoudre avant ou pendant impl)

1. **Picovoice Porcupine wake-word toujours actif pendant le confirm vocal ?** Risque : user dit « oui » mais le stream wake-word entend « MamY... » dans un mot et déclenche une nouvelle capture. → Décision : pause wake-word pendant le confirm (3 sec), restart après.

2. **SpeechRecognizer Google requires Google Play Services + network ?** → Sur Android 13+ on-device speech available. Sur older Android, network required. Test sur 2-3 devices old (Android 9-10).

3. **Confirmation vocale : Whisper short-utterance vs SpeechRecognizer ?** SpeechRecognizer plus rapide pour yes/no (<500 ms latency vs Whisper ~1-2 sec). Choix : SpeechRecognizer V1, swap pour Whisper si privacy mode strict (V2).

4. **Levenshtein distance threshold** : 2 OK pour « Jimmy → Jimmie » mais peut faire des faux positifs (« Jim » → « Tim »). À tuner empiriquement avec corpus de tests.

5. **Phone E.164 normalization** : user contacts peuvent avoir « 514-555-1234 » ou « (514) 555-1234 » ou « +15145551234 ». Use Google libphonenumber pour normalize. Dep size ~600 KB acceptable.

6. **NER ONNX model** (V2) : MobileBERT-tinyNER FR+EN distillé, ~8 MB. Ou TFLite Mobile NER. Benchmark à faire en V2 — pas V1 concern.

7. **What si user a 0 contact dans Android (tel neuf)** ? → ContactsRepository emit emptyList → tout match est None → TTS « *Pas de Jimmy dans tes contacts. Tu peux l'ajouter dans tes contacts Android et réessayer.* »

8. **What si Person table existe mais pas de Contact match** ? Person Marie (matched par calendar P5) mais pas dans Android Contacts → TTS « *Marie n'a pas de numéro de téléphone enregistré* ». Solution V2 : on importe le tel depuis le calendar metadata si disponible, sinon V2 ContactCreator vocal.

---

## 13. Glossaire

- **text_to** : nouveau voice intent pour envoyer un SMS
- **SmsManager** : Android system service pour envoyer SMS
- **PendingIntent** : Android wrapper pour callbacks différés
- **BroadcastReceiver** : Android receveur pour les events système
- **ContactsContract** : Android ContentProvider pour les contacts
- **NER** : Named Entity Recognition — extraction de noms propres
- **E.164** : format international standard de numéro de téléphone (`+15145551234`)
- **GSM-7** : encoding 7-bit pour SMS, 160 chars/segment
- **UCS-2** : encoding 16-bit pour SMS multilingues, 70 chars/segment
- **ONNX** : Open Neural Network Exchange, format de modèle ML cross-platform
- **HomonymeClarifier** : composant P4 qui demande à l'user de désambiguïser entre 2+ matches

---

## 14. Annexes

### A. Voice grammar regex complet (parser local)

```kotlin
// IntentGrammar.kt — full text_to additions

private val TEXT_TO_FR = Regex(
    "^MamY,?\\s+(?:texte|envoie\\s+(?:un\\s+)?(?:texto|sms)|dis)\\s+" +
    "(?:à\\s+)?(?<who>[\\w\\-'\\u00C0-\\u017F]+(?:\\s+[\\w\\-'\\u00C0-\\u017F]+)?)" +
    "\\s+(?:que\\s+|dis(?:-?lui)?\\s+|:\\s*|,\\s*)" +
    "(?<body>.+)$",
    RegexOption.IGNORE_CASE
)

private val TEXT_TO_EN = Regex(
    "^MamY,?\\s+(?:text|send\\s+(?:a\\s+)?(?:text|sms)\\s+to)\\s+" +
    "(?:to\\s+)?(?<who>[\\w\\-']+(?:\\s+[\\w\\-']+)?)" +
    "\\s+(?:that\\s+|saying\\s+|:\\s*|,\\s*)" +
    "(?<body>.+)$",
    RegexOption.IGNORE_CASE
)

private val CONFIRM = Regex(
    "^\\s*(oui|yes|ok|envoie|envoyer|confirm[éée]?|vas-?y|go)\\s*$",
    RegexOption.IGNORE_CASE
)

private val CANCEL = Regex(
    "^\\s*(non|no|annule(?:r)?|stop|arr[êe]te|cancel)\\s*$",
    RegexOption.IGNORE_CASE
)

// Append to existing intents map
intents.add(IntentPattern(TEXT_TO_FR, ::buildTextTo))
intents.add(IntentPattern(TEXT_TO_EN, ::buildTextTo))
```

### B. Module structure

```
app/src/main/kotlin/com/mamy/android/
├── data/contacts/
│   ├── ContactsRepository.kt
│   ├── Contact.kt
│   ├── PhoneNumber.kt
│   ├── PhoneType.kt (enum)
│   ├── ContactMatcher.kt
│   └── MatchResult.kt
├── data/sms/
│   ├── SmsSender.kt
│   ├── SmsResult.kt
│   ├── SmsStatusReceiver.kt
│   ├── VoiceConfirmListener.kt
│   └── SmsRetryWorker.kt (V1.1)
├── data/db/
│   ├── entity/SentSmsEntry.kt (NEW)
│   └── dao/SentSmsDao.kt (NEW)
├── domain/intent/
│   ├── handler/TextToHandler.kt (NEW)
│   ├── Intent.kt (EXTEND)
│   └── IntentGrammar.kt (EXTEND)
├── ui/screens/
│   ├── person/PersonDetailScreen.kt (EXTEND : SMS tab)
│   ├── settings/SettingsScreen.kt (EXTEND : SMS section)
│   ├── onboarding/OnboardingScreen.kt (EXTEND : SMS step 4/6)
│   └── data/DataScreen.kt (EXTEND : SMS history)
└── di/
    ├── ContactsModule.kt (NEW)
    └── SmsModule.kt (NEW)
```

### C. Risques techniques connus

| # | Risque | Mitigation |
|---|---|---|
| R1 | SEND_SMS permission Play Store policy | V1 sideload only, V1.5 Play Store review avec use case justification documentée |
| R2 | False-positive wake-word pendant confirm | Pause wake-word pendant 3 sec confirm window |
| R3 | SpeechRecognizer requires network sur old Android | Fallback : tap on-screen Confirm/Cancel buttons (UI escape hatch) |
| R4 | Phone E.164 normalization edge cases (numéros internationaux) | Use Google libphonenumber lib (Maven Central, ~600 KB) |
| R5 | Levenshtein false positives sur noms courts (Jim/Tim) | Tuner threshold empiriquement, possibilité user override per-contact |
| R6 | NER false negatives V2 (un nom passe au cloud) | Double-pass extraction + user re-validate avant send + log redaction success rate dans NetworkLog |
| R7 | LLM fallback latency >5s (timeout) | Cancel + TTS « reformule » ; mode STRICT évite le problème |
| R8 | Contact deleted between lookup et send | Re-validate avant send ; si orphelin → cancel |
| R9 | Multi-SIM device : quel SIM envoie le SMS ? | V1 = default SIM (system-level setting). V2 = setting MamY explicite |
| R10 | SMS received pendant le confirm flow | Pas un risque : SMS received est out-of-scope V1 (pas de READ_SMS) |

### D. Setup user TODO post-install (~5 min)

1. Sideload APK depuis GitHub Releases
2. Premier launch → Onboarding
3. Step 1 : permission RECORD_AUDIO + FOREGROUND_SERVICE_MICROPHONE
4. Step 2 : permission POST_NOTIFICATIONS
5. Step 3 : entrer Picovoice AccessKey + drop mamy_en.ppn / mamy_fr.ppn (depuis Picovoice Console pré-trained)
6. Step 4 : entrer BYOK Claude/GPT/Gemini API key
7. **Step 5 (NEW P9)** : permissions READ_CONTACTS + SEND_SMS, opt-in SMS feature
8. Step 6 : connect Google Calendar OAuth (optionnel)
9. Step 7 : test wake-word « MamY test 1 2 3 »
10. All set → ReportsListScreen

---

## 15. Notes implementation (writing-plans next)

P9 SMS feature peut être splitée en ~18 tasks TDD bite-sized :

1. Add deps (libphonenumber, accompanist-permissions extension if needed)
2. ContactsRepository + Contact data classes
3. ContactsRepository tests (Robolectric ContentResolver mock)
4. ContactMatcher cascade implementation
5. ContactMatcher tests (golden inputs + fuzzy edge cases)
6. PhoneType enum + phone selection priority
7. Person.android_contact_id migration + auto-link sync
8. SentSmsEntry entity + SentSmsDao + register dans MamYDatabase v3
9. SentSmsDao tests
10. SmsSender + SmsResult sealed class
11. SmsStatusReceiver + PendingIntent setup
12. SmsSender tests (mock SmsManager)
13. VoiceConfirmListener (SpeechRecognizer wrapper)
14. VoiceConfirmListener tests (Robolectric)
15. IntentGrammar extension : text_to regex FR + EN + tests
16. Intent.TextTo case + Router + Dispatcher routing
17. TextToHandler orchestrator (full flow)
18. TextToHandler integration tests (in-memory DB + mocked SmsSender)
19. ContactsModule + SmsModule Hilt
20. PersonDetail SMS tab Composable + ViewModel
21. Settings SMS section Composable
22. Onboarding step 4/6 Composable
23. DataScreen SMS history section
24. VoiceIndicator state addition
25. Permissions runtime flow integration
26. End-to-end manual smoke checklist

= ~26 tasks TDD bite-sized, ~5-7 j dev solo + Claude avec parallel agents.

---

**Prêt pour writing-plans.** Spec sauvé à `D:/ComfyUI-Intel/mamy/docs/superpowers/specs/2026-05-03-mamy-p9-sms-vocal-design.md`.
