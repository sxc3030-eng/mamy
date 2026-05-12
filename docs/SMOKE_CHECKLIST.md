# MamY V1.5 Alpha — Smoke Checklist (v0.4.5)

> Pre-distribution checklist run by the release engineer + by each new alpha
> tester on first install. 22 scenarios covering install → onboarding →
> bottom-nav → voice capture (Reports / Notes / Actions) → calendar /
> reminders → briefings → SMS → settings.

**Target hardware:** Pixel 6 / Pixel 7 / Galaxy S22 (or any Android 9+ device
with mic and Gboard / Samsung Voice / equivalent system STT engine).

**Required setup before testing:**

- Just an Android 9+ device. **No Picovoice key, no `.ppn`, no LLM API key
  needed for v0.4.5-alpha** — the build embeds a zero-setup config that
  routes inference through the i5 Cloudflare tunnel to Groq Cloud.
- A working SIM if scenario 14 (SMS) is in scope.

---

## 1. Sideload install

- **Test:** Open <https://friends-muscle-warriors-formula.trycloudflare.com/>
  on the device, tap "Télécharger l'APK", accept "Install unknown app"
  prompt for the browser, tap **Install**.
- **Expected:** App icon appears in launcher, named "MamY".
- **Retry if fails:** Verify Android version ≥ 9.0. Check device storage >
  200 MB free. Try reinstalling from Files app instead of browser.

## 2. First launch + permissions

- **Test:** Tap MamY icon. Grant `RECORD_AUDIO` + `POST_NOTIFICATIONS` at
  prompt.
- **Expected:** Onboarding step 1 of 6 appears within 2 seconds.
- **Retry if fails:** Settings > Apps > MamY > Permissions > grant manually.

## 3. Onboarding — 6 steps complete

- **Test:** Walk through all 6 onboarding screens:
  1. Permissions
  2. Wake-word — tap **"Use built-in JARVIS"** (no AccessKey needed)
  3. SMS opt-in (you can skip)
  4. Calendar — grant `READ_CALENDAR`
  5. Wake-word test — say "Jarvis", expect chime
  6. Done → lands on Reports tab
- **Expected:** Each "Continue" advances; final "Start" lands on home.
- **Retry if fails:** If a screen freezes, force-stop app (Settings > Apps),
  reopen.

## 4. Bottom navigation — 5 tabs render

- **Test:** Tap each bottom-nav item: Rapports, Actions, Agenda, Notes,
  Réglages.
- **Expected:** Each tab renders without crash; selected indicator highlights.
- **Retry if fails:** Check Compose runtime not stripped by R8. Open
  Settings > Logs > Share for stack trace.

## 5. Wake-word "Jarvis" detected (English)

- **Test:** With device idle on any tab, say "Jarvis" once normally
  (~30 cm from mic).
- **Expected:** Foreground-service notification updates to "Listening" +
  audio chime within ~1 s.
- **Retry if fails:** Check the foreground-service notification is present
  (scenario 18). Speak louder. Try in a quiet room. If still nothing, scenario
  5 fails — proceed with scenario 7 (manual Record FAB).

## 6. Wake-word "Jarvis" in French context

- **Test:** Réglages > Langue > Français. Reopen MamY. Say "Jarvis" with
  a French accent.
- **Expected:** Same chime + notif transition.
- **Retry if fails:** Built-in JARVIS is language-agnostic — failure here
  more likely means the engine never started (check scenario 18).

## 7. Voice debrief via Record FAB — visual feedback

- **Test:** Rapports tab > tap the green **"Enregistrer"** FAB at the
  bottom-right.
- **Expected:** Toast **"Mamy écoute… (90s max)"** appears immediately.
  The foreground-service notification switches to "Recording".
- **Retry if fails:** If no Toast, the FAB wiring is broken — file an issue
  with scenario 7 and device logs.

## 8. Voice debrief — auto-structure via Ollama tunnel

- **Test:** From the Record FAB triggered in scenario 7, say "Debriefing
  1:1 avec Marie. Elle dit que le projet Q3 est en retard de deux semaines."
  Stop talking → wait for VAD silence (~1.5 s).
- **Expected:** Within 5–10 s after silence, a new entry appears under
  Rapports with `person=Marie`, structured fields populated. No manual
  "Structure" tap needed — Ollama provider runs automatically.
- **Retry if fails:** Open Réglages > LLM > "Test connection" → expect
  "✓ ollama connection OK". If KO, the tunnel is down or your phone has no
  network. Settings > Logs > Share for the stack.

## 9. Voice FAB Notes — direct dictation

- **Test:** Notes tab > tap the round **🎤** FAB (above the "Nouvelle note"
  FAB). Dicte "Première note vocale d'essai".
- **Expected:** System STT dialog opens (Gboard / Samsung Voice). Result
  saves directly as a Note in the list — no intermediate text dialog.
- **Retry if fails:** If the STT dialog never opens, the device has no
  system speech engine — install Gboard or Samsung Voice and retry.

## 10. Voice FAB Actions — direct dictation

- **Test:** Actions tab > tap the round **🎤** FAB. Dicte "Rappeler Marie
  demain".
- **Expected:** Action row appears with `description = "Rappeler Marie
  demain"`, `assignee = "Me"`, no deadline.
- **Retry if fails:** Same as scenario 9 — verify system STT.

## 11. Mic icon in dialogs (proper Mic glyph)

- **Test:** Notes tab > tap "+ Nouvelle note" → in the dialog, tap the mic
  icon at the right of the Title field.
- **Expected:** Icon shows a **microphone** (not an envelope). Tapping it
  opens the system STT dialog.
- **Retry if fails:** If you still see the envelope icon, you're on an
  older build than v0.4.5-alpha — reinstall.

## 12. Persistence after restart

- **Test:** Force-stop the app, reopen. Navigate to Rapports.
- **Expected:** Captures from scenarios 8 + 9 + 10 are still there.
- **Retry if fails:** Check encrypted DB initialized. Check SQLCipher
  passphrase not corrupt (Settings > Logs).

## 13. Daily briefing

- **Test:** Rapports tab > "Today's agenda" card > play. (Or Settings >
  Briefings > Daily > play.)
- **Expected:** TTS reads aloud actions/promises due today.
- **Retry if fails:** Add at least one capture with a date-bound action
  first (scenario 8).

## 14. Pre-meeting briefing

- **Test:** Open a Person card (tap a row in Rapports) > Briefings > play.
- **Expected:** Summary of recent interactions with that person.
- **Retry if fails:** Verify person was created during scenario 8.

## 15. Person query briefing (voice)

- **Test:** Say "Jarvis, brief me on Marie" / "Jarvis, briefe-moi sur
  Marie".
- **Expected:** TTS speaks a summary of Marie's recent interactions.
- **Retry if fails:** Check TTS engine (Settings > Accessibility >
  Text-to-speech).

## 16. SMS via voice

- **Test:** Say "Jarvis, texte à Marie que je serai en retard de 10
  minutes". Say "Oui" to confirm.
- **Expected:** SMS appears in Android Messages > Sent. New row in Person
  > SMS history tab.
- **Retry if fails:** Verify `SEND_SMS` + `READ_CONTACTS` permissions
  (scenario 3 SMS step). Verify Marie has a phone number in Contacts.

## 17. Calendar tab — phone agenda

- **Test:** Agenda tab.
- **Expected:** List of upcoming meetings from the phone's calendar
  (Google / Outlook / Samsung / iCloud — whichever the device syncs).
  Each row shows title + start time + attendee count.
- **Retry if fails:** Verify `READ_CALENDAR` granted. Add a test event via
  Google Calendar app to confirm sync.

## 18. Meeting reminder notifications (24h + 1h)

- **Test:** Create a calendar event 24 h + 5 min from now with at least
  one attendee. Wait 5 min.
- **Expected:** Notif "Demain : <event title>" arrives (24h reminder
  channel). 23 h later, "Dans 1 heure : <event>" arrives.
- **Retry if fails:** Check Réglages > Notifs > "Meeting reminders" channel
  enabled. WorkManager may take up to a few minutes to schedule.

## 19. Foreground service indicator

- **Test:** With wake-word listening on (post-onboarding), swipe down
  notification shade.
- **Expected:** Persistent "MamY is listening" notification with mic
  icon.
- **Retry if fails:** Check `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`
  permissions. Restart device.

## 20. Data export (encrypted JSON)

- **Test:** Réglages > Data > Export. Enter passphrase. Pick destination
  via Storage Access Framework.
- **Expected:** A `.mamy-export.gz.aes` file is written to the chosen
  folder. Size > 1 KB.
- **Retry if fails:** Check write permission on chosen folder. Try internal
  storage instead of SD card.

## 21. Network airplane mode resilience

- **Test:** Enable airplane mode. Tap Record FAB (scenario 7), speak.
- **Expected:** Toast appears. Capture saves locally (transcript via
  on-device Whisper). Structuring fails gracefully — Settings > Logs shows
  "tunnel unreachable", but no crash, capture is still recoverable.
- **Retry if fails:** Check error handling in OllamaProvider — should fall
  back gracefully without crashing the service.

## 22. Battery drain (8 h overnight)

- **Test:** Plug in to 100 %, unplug, leave with wake-word on for 8 hours.
- **Expected:** Battery drop < 15 % overnight (target: 1–2 %/hour idle).
- **Retry if fails:** Settings > Battery > MamY > restrict background usage
  if > 5 %/hour. Tune wake-word sensitivity in Réglages.

---

## Pass criteria

- Scenarios 1–17 pass on at least one Pixel 7 / Galaxy S22 reference device.
- Tester completes the checklist within 30 minutes (excluding scenario 22
  battery test).
- Crash log (Settings > Logs > Share) is empty after the run.
- Scenario 5 (wake-word) is **expected to be "Jarvis"** until custom
  `mamy_*.ppn` is shipped — file scenario 5 fails ONLY if "Jarvis" doesn't
  trigger.

## Reporting

File issues at <https://github.com/sxc3030-eng/mamy/issues> with:

- Device model + Android version
- Failed scenario number from this checklist
- Logs from Réglages > Logs > Share (no PII / no audio is logged)
- Screen recording for any failed scenario (Android > Quick Settings >
  Screen Record)
