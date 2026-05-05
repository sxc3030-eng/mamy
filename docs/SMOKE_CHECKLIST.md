# MamY V1 Alpha — Smoke Checklist

> Pre-distribution checklist run by the release engineer + by each new alpha
> tester on first install. ~20 scenarios covering install → onboarding →
> capture → briefing → SMS → settings.

**Target hardware:** Pixel 6 / Pixel 7 / Galaxy S22 (or any Android 9+ device with mic).

**Required setup before testing:**

- Picovoice AccessKey (free at console.picovoice.ai)
- Trained `mamy_en.ppn` + `mamy_fr.ppn` files (Picovoice Console > Wake Word > train)
- Anthropic Claude API key (sk-ant-...)

---

## 1. Sideload install

- **Test:** Open GitHub Release page on device, tap `app-release.apk`, accept
  "Install unknown app" prompt for Chrome/browser, tap Install.
- **Expected:** App icon appears in launcher, named "MamY".
- **Retry if fails:** Verify Android version >= 9.0. Check device storage > 200 MB free.
  Try reinstalling from Files app instead of browser.

## 2. First launch + permissions

- **Test:** Tap MamY icon. Grant RECORD_AUDIO + POST_NOTIFICATIONS at prompt.
- **Expected:** Onboarding step 1 of 7 appears within 2 seconds.
- **Retry if fails:** Settings > Apps > MamY > Permissions > grant manually.

## 3. Onboarding 7 steps complete

- **Test:** Walk through all 7 onboarding screens (welcome → mic check → wake
  word setup → Picovoice key → Claude key → notification perms → done).
- **Expected:** Each "Next" advances; final "Start" lands on home.
- **Retry if fails:** If a screen freezes, force-stop app (Settings > Apps), reopen.

## 4. Wake-word "MamY" detected (English)

- **Test:** With device idle on home screen, say "MamY" once normally.
- **Expected:** Visual + audio cue (haptic + chime) confirms detection within 1 s.
- **Retry if fails:** Check Picovoice key not expired. Check `mamy_en.ppn` placed
  at `Documents/MamY/wakeword/`. Speak louder, 30 cm from mic.

## 5. Wake-word "MamY" detected (French)

- **Test:** Settings > Language > switch to French. Say "MamY" with French accent.
- **Expected:** Same haptic + chime confirms detection.
- **Retry if fails:** Verify `mamy_fr.ppn` present alongside `mamy_en.ppn`.

## 6. Volume-up fallback

- **Test:** From home, press Volume Up button twice quickly.
- **Expected:** Same activation cue as wake-word.
- **Retry if fails:** Check accessibility service granted; restart phone.

## 7. Voice debrief capture

- **Test:** Activate wake-word. Say "Debriefing 1:1 with John. He said the
  Q3 roadmap is delayed by two weeks." Stop talking.
- **Expected:** Toast "Capture saved", entry appears in Recent Captures.
- **Retry if fails:** Check mic working in another app. Check VAD threshold.

## 8. Structuring (Claude API)

- **Test:** Open the capture from step 7 → Tap "Structure".
- **Expected:** Within 5-10 s, structured fields populate (person=John,
  topic=Q3 roadmap, action=delay 2 weeks).
- **Retry if fails:** Check Claude API key + network connectivity. Inspect
  Settings > Logs.

## 9. Persistence after restart

- **Test:** Force-stop the app, reopen. Navigate to Recent Captures.
- **Expected:** The capture from step 7 + 8 is still there with structured fields.
- **Retry if fails:** Check encrypted DB initialized. Check SQLCipher passphrase
  not corrupt.

## 10. Daily briefing

- **Test:** Home > "Briefings" > "Daily". Tap.
- **Expected:** Briefing list shows actions/promises due today, generated text
  reads aloud automatically.
- **Retry if fails:** Add at least one capture with a date-bound action first.

## 11. Pre-meeting briefing

- **Test:** "Briefings" > "Pre-meeting" > pick John (from the test capture).
- **Expected:** Summary of recent interactions with John, last actions, open promises.
- **Retry if fails:** Verify person was created during structuring step.

## 12. Person query briefing

- **Test:** Activate wake-word → "Briefe-moi sur John" / "Brief me on John".
- **Expected:** Voice-spoken summary of John's recent interactions.
- **Retry if fails:** Check TTS engine installed (Settings > Accessibility >
  Text-to-speech).

## 13. End-of-day briefing

- **Test:** "Briefings" > "EOD". Tap at end of day.
- **Expected:** Summary of completed actions + open items + tomorrow preview.

## 14. SMS via voice

- **Test:** Activate wake-word → "MamY texte à John que je serai en retard de 10 minutes".
  Confirm send.
- **Expected:** SMS appears in Android Messages > Sent. Captured as a SentSmsEntry.
- **Retry if fails:** Verify SEND_SMS + READ_CONTACTS permissions. Verify John
  has a phone number in Contacts.

## 15. Data export (encrypted JSON)

- **Test:** Settings > Data > Export. Enter passphrase. Pick destination via
  Storage Access Framework.
- **Expected:** A `.mamy-export.gz.aes` file is written to the chosen folder.
  Size > 1 KB.
- **Retry if fails:** Check write permission on chosen folder. Try internal
  storage instead of SD card.

## 16. Data wipe

- **Test:** Settings > Data > Wipe all data. Confirm twice.
- **Expected:** All captures, persons, briefings disappear. Onboarding restarts
  on next launch.
- **Retry if fails:** If app crashes mid-wipe, uninstall + reinstall.

## 17. All 7 screens render

- **Test:** Navigate to: Home, Captures, People, Briefings, Settings, Onboarding,
  Play Briefing (deep link `mamy://play`).
- **Expected:** Each screen renders without crashes; toolbar + content visible.
- **Retry if fails:** Check Compose runtime not stripped by R8.

## 18. Foreground service indicator

- **Test:** With wake-word listening on, swipe down notification shade.
- **Expected:** Persistent "MamY is listening" notification with mic icon.
- **Retry if fails:** Check FOREGROUND_SERVICE + FOREGROUND_SERVICE_MICROPHONE
  permissions. Restart device.

## 19. Battery drain (8h overnight)

- **Test:** Plug in to 100 %, unplug, leave with wake-word on for 8 hours.
- **Expected:** Battery drop < 15 % overnight (target: 1-2 %/hour idle).
- **Retry if fails:** Settings > Battery > MamY > restrict background usage if
  > 5 %/hour. Tune VAD threshold.

## 20. Network airplane mode resilience

- **Test:** Enable airplane mode. Capture a debrief. Try Structure.
- **Expected:** Capture still saves locally. Structure fails gracefully with
  "no network" toast (no crash).
- **Retry if fails:** Check OkHttp timeout configured. Check error handling
  in the structuring service.

---

## Pass criteria

- All 20 scenarios pass on at least one Pixel 7 reference device.
- Tester completes the checklist within 30 minutes (excluding battery test).
- Crash log (Settings > Logs > Share) is empty after the run.

## Reporting

Send results to the alpha tester Slack channel `#mamy-alpha` with:
- Device model + Android version
- Pass/fail per scenario number
- Crash log file (if any) attached
- Screen recording for any failed scenario (Android > Quick Settings > Screen Record)
