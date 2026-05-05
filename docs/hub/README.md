# MamY — Hub de suivi projet

Dossier de tracking pour le projet MamY. **Le code vit ailleurs** — voir liens ci-dessous.

## 📍 Où vit quoi

| Quoi | Où |
|---|---|
| **Code source** | `D:\ComfyUI-Intel\mamy\` (git repo, GitHub privé `sxc3030-eng/mamy`) |
| **Spec design original** | `D:\ComfyUI-Intel\mamy\docs\superpowers\specs\2026-05-02-mamy-design.md` (652 lignes, sections 1-13) |
| **8 plans d'impl P1→P8** | `D:\ComfyUI-Intel\mamy\docs\superpowers\plans\2026-05-02-mamy-p<N>-*.md` |
| **CONVENTIONS.md** | `D:\ComfyUI-Intel\mamy\docs\superpowers\plans\CONVENTIONS.md` |
| **APK build output** | `D:\ComfyUI-Intel\mamy\app\build\outputs\apk\debug\app-debug.apk` |
| **Handoffs sessions Claude** | `~\.claude\projects\D--ComfyUI-Intel\memory\handoff_mamy_*.md` |

## 📂 Ce dossier (D:\mamy\) contient

- **README.md** — ce fichier
- **STATUS.md** — état live du projet (versions, tests, commits, ce qui marche / ce qui manque)
- **ORIGINAL_VISION.md** — récap du wedge original (rassurance que rien n'a été oublié)
- **ROADMAP.md** — P7, P8, P9 SMS en attente, échéancier
- **briefings/** — copies locales des handoffs critiques pour consultation hors session Claude

## 🎯 Pitch en 1 phrase

> App Android voice-first qui transforme le debrief vocal post-1:1 (60-90 sec) en mémoire vivante par employé pour managers de 30 à 100 personnes — briefe avant chaque rencontre, traque les promesses qui dérapent, **et envoie tes SMS à la voix** (P9 nouvelle feature).

## 🚦 Statut court

- **P1→P6 SHIPPED** (2026-05-03) : pipeline backend complète, 281 tests pass, APK build green.
- **P7 UI Compose** WIP sur branche `p7-ui` (3 commits)
- **P8 Privacy Polish Beta** pas commencé
- **P9 SMS feature vocal** (NEW) : design en cours, spec à écrire

## ⚡ Commands rapides

```bash
# État git du projet
cd D:\ComfyUI-Intel\mamy
git log --oneline -10
git tag | grep mamy

# Build APK debug
./gradlew :app:assembleDebug

# Run tests
./gradlew :app:testDebugUnitTest

# APK installable sur tel
adb install app\build\outputs\apk\debug\app-debug.apk
```
