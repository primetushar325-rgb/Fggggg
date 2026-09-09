# 🎮 GameSound Pro — Premium Gaming Soundboard for Android

<p align="center">
  <em>“Your Gaming Soundboard” — meme sounds, effects, music clips and your own recordings,<br>
  one tap away — with an optional floating overlay for Gaming Mode.</em>
</p>

GameSound Pro is a **production-quality, offline-first soundboard** built with Kotlin and
Jetpack Compose. It is a **completely independent Android app**: it plays audio through
Android's public media APIs like any normal music player, and it **never touches games** —
no game files, no game memory, no injection, no hooks, no anti-cheat interaction, no root.

---

## ✨ Features

| Area | What you get |
| --- | --- |
| **Soundboard** | Category chips (Memes, Reactions, Funny, Music, Gaming, Voice, Effects, Custom), premium animated glass cards, instant playback with overlapping sounds, playing-state glow, favorites, duration labels |
| **Add sounds in-app** | `+ Add Sound` → import a single file with full customization (name, category, emoji icon, volume, trim, preview), quick multi-file import from the device, record voice, create pack. Supports MP3, WAV, M4A, OGG (+ AAC, FLAC, OPUS) |
| **Recording** | Start / pause / resume / stop, live level meter + timer, then the same metadata form (rename, trim, preview, save). Microphone permission is requested **only when you open the recorder** |
| **Sound packs** | Create / rename / delete packs, add & remove sounds, reorder inside a pack, **export / import packs** as validated `.gsoundpack.zip` archives |
| **Favorites** | Star any sound; favorites appear on their tab, on Home, and in the floating overlay |
| **Floating Gaming Overlay** | Draggable bubble above other apps (official `SYSTEM_ALERT_WINDOW` mechanism), expands to a compact soundboard: quick sounds, play/stop, volume, stop-all, exit. Size + position persisted. Never blocks the game |
| **Gaming Mode** | One toggle enables the overlay, densifies the grid, disables decorative animations, and adds a double-back-to-exit guard |
| **Audio mixer** | Effects / Music / Voice / Master volume sliders, global mute, Stop All, and optional **music ducking** while an effect plays |
| **Mini music player** | Plays every sound tagged *Music*: play/pause, next/previous, seek, loop, shuffle, volume, embedded artwork, background playback with a media notification (proper foreground service) |
| **Search, sort, filter** | Global search across sound name, category and pack name; sort by Recently added / Recently played / Most played / Favorites / A–Z |
| **Sound management** | Long-press any sound → edit, rename, change icon/category, trim, duplicate, move to pack, delete (with confirmation) |
| **Storage management** | Sounds / packs / MB used, cache clearing, delete never-played sounds, pack export & import |
| **Starter content** | Six synthesized WAV effects ship in the app, so the board is never empty on first launch |

## 🛡️ V2 stability architecture (audio + floating overlay)

V2 hardens the two subsystems that matter most in-game:

- **One authoritative AudioEngine** (`Application`-scoped, owned by no Activity/Service/UI).
  Every consumer — app screens, the floating sidebar, the notification service — observes a
  single `AudioSnapshot` state machine (`IDLE / LOADING / PLAYING / PAUSED / STOPPED / ERROR`)
  and never touches player lifecycle. Commands are serialized; slots self-heal by rebuilding
  any player that errored and retrying once, so routing changes (entering a game, BT
  connect/disconnect) can't leave a permanently silent pool.
- **Explicit audio-focus policy** (Settings → Playback): *Independent* (default — never
  requests focus, game audio untouched) or *Duck others* (transient-may-duck while effects
  play). Focus state is visible in Audio Diagnostics.
- **Overlay state machine**: `OverlayStateMachine` (pure, unit-tested) holds
  `BUBBLE_ONLY ↔ SIDEBAR_OPEN`. The service is a thin renderer that reconciles windows to
  match state — closing the sidebar detaches *only* the sidebar window: the service, the
  bubble, Gaming Mode and all audio keep running. Toggle is atomic with a 220 ms debounce.
  All overlay geometry goes through crash-safe range helpers (V1 could throw and kill the
  service when the panel exceeded small/landscape screens).
- **Sidebar persistence**: filter/search state, volume, position, size and Gaming Mode
  survive sidebar close/reopen (and service restart); a clean `BUBBLE_ONLY` state is
  restored after process death.
- **Audio Diagnostics** (Settings → Audio diagnostics): engine/player/focus/volume/route/
  service/gaming-mode rows plus a **TEST SOUND** button that reports
  “✓ Audio Engine Active” or “⚠ Audio Playback Unavailable” with the reason (never silent).
- **Structured debug logs** in debug builds: `[OverlayService] [OverlayState] [AudioEngine]
  [AudioFocus] [Playback] [Permission]`.

## 🧭 V3 — floating icon gestures + audio routing diagnostics

- **Tap/drag separation done properly** (`FloatingIconTouchController`): the platform's
  density-correct touch slop classifies each gesture through an explicit
  `IDLE → TRACKING → DRAGGING` state machine. A drag can *never* toggle the sidebar; a tap
  never moves the icon. On release the icon **snaps to the nearest screen edge**.
- **Orientation-safe position memory**: the icon position is persisted as *fractions of the
  movable area*, so a position saved in portrait restores correctly in landscape (and after
  restart) instead of landing off-screen. Configuration changes re-clamp the icon and
  rebuild the sidebar for the new bounds.
- **Audio Routing Diagnostics** (Settings → Audio diagnostics): microphone/overlay
  permissions, engine, focus, live output route (speaker/wired/Bluetooth — updates on plug
  events via `AudioDeviceCallback`, no polling), microphone hardware availability, Gaming
  Mode — plus an **Audio Mode** panel that lists only outputs this device actually has.
- **Three-step AUDIO TEST**: ① built-in test tone with an explicit “Can you hear this?”
  YES/NO, ② an explicit 3-second microphone sample (`🔴 RECORDING`, `STOP NOW`, local
  playback only), ③ a verdict — ending with the honest line:
  **CROSS-APP MIC LOOPBACK: NOT AVAILABLE THROUGH STANDARD ANDROID API**, with the
  explanation of why teammates can't hear the soundboard through game voice chat (no game
  modification, no injection — by design).
- Duplicate-service guard: enabling Gaming Mode twice never starts a second
  `OverlayService`; `DebugLog` tags now include `[Overlay] [Drag] [Click] [Sidebar]
  [AudioRoute]`.

## 🛡️ Safety, privacy & game compatibility

- **No game interaction, ever.** The app cannot and does not modify game APKs, read game
  memory, inject code, bypass anti-cheat, automate gameplay, or touch any other app's
  processes. It is a plain media app.
- **Microphone** is used only while you are actively recording. No background recording.
- **All audio stays on-device**, in the app's private storage, excluded from backups.
  Nothing is uploaded anywhere — the app has no network code at all.
- **Minimum permissions**: microphone (recording only), notifications (playback/gaming
  notifications), overlay ("display over other apps", only when you enable Gaming Mode).
  No storage permission (SAF is used), **no contacts / SMS / location / camera /
  accessibility service / root**.
- **Voice-chat note:** Android does not let a normal app route its audio into another app's
  microphone/voice-chat pipeline, and GameSound Pro deliberately does not try to bypass
  that. Sounds play through your device's normal audio output (speaker/headphones). If you
  want teammates to hear a sound, play it out loud — or use the in-game media features your
  game provides. This limitation is explained in-app (Settings → About / Privacy).

## 🏗️ Architecture

```
app/src/main/kotlin/com/gamesoundpro/app/
├── GameSoundProApp.kt      # Application: container, notification channels, seeding
├── MainActivity.kt         # Single activity (Compose), auto-stop on background
├── di/AppContainer.kt      # Hand-rolled DI: explicit, fast cold start
├── database/               # Room: entities, DAOs, database
├── domain/                 # Pure models + LibraryFilter (unit-tested)
├── audio/                  # AudioEngine (Media3/ExoPlayer), VoiceRecorder
├── service/                # PlaybackNotificationService (foreground mediaPlayback)
├── overlay/                # OverlayService (floating bubble) + GamingModeManager
├── repository/             # SoundRepository (metadata + files), PackCodec (zip)
├── settings/               # DataStore-backed SettingsRepository
├── permissions/            # Permission helpers (minimum-permission policy)
├── ui/
│   ├── theme/              # Dark gaming theme, accents, glassmorphism primitives
│   ├── navigation/         # AppRoot: bottom bar, sheets, snackbars, gaming banner
│   ├── components/         # SoundTile, chips, dialogs, empty states
│   ├── home/ soundboard/ mysounds/ favorites/ settings/ player/ sheets/
└── utils/                  # Formatting, audio file helpers
```

**Stack:** Kotlin 2.0 · Jetpack Compose + Material 3 · Room (KSP) · Media3/ExoPlayer ·
DataStore · Coroutines/Flow · MVVM with a thin repository layer. Targets Android 8.0+
(minSdk 26), compile/target SDK 34.

**Performance notes:** small effect-player pool with eager release of finished players,
no image-loading library (artwork extracted + downsampled via platform APIs), no
reflection-based DI, animation reduction in Gaming Mode, lazy overlay service that only
runs while Gaming Mode is enabled.

## 🔨 Build

Requirements: **JDK 17**, Android SDK (Android Studio Ladybug+ or command line).

```bash
./gradlew :app:assembleDebug      # debug APK  -> app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest  # JVM unit tests
./gradlew :app:assembleRelease    # unsigned unless signing env vars are set (below)
```

Or open the folder in Android Studio and press Run ▶.

### GitHub Actions

`.github/workflows/android.yml` builds on every push/PR: installs JDK 17, runs
`assembleDebug` + unit tests, and uploads the **debug APK as a workflow artifact**
(Actions → Android CI → build → artifacts).

Release builds run on `v*` tags with secrets-based signing — see **docs/RELEASE.md**.
Signing keys and passwords are never stored in the repository.

## 📲 First run

1. Install the debug APK and open GameSound Pro — six starter sounds are seeded
   automatically (tap one!).
2. **Add your own:** tap ➕ on the Soundboard (or Home → Add Sound). Pick an MP3/WAV/M4A/OGG,
   name it, choose an emoji, trim it, preview, save. It appears in *My Sounds* instantly.
3. **Record:** Home → Record → allow the microphone when asked → record, pause/resume,
   stop, trim, save.
4. **Star** sounds to fill Favorites and the overlay's quick buttons.
5. **Gaming Mode:** flip it on Home or in Settings. The first time, Android will ask you to
   allow "Display over other apps" — that's the official overlay permission. Then a 🎵
   bubble floats above your game: drag it anywhere, tap it for quick sounds, volume and
   stop-all.

## ⚠️ Documented limitations (honest engineering)

- **Voice-chat routing is impossible via public APIs** — see the safety section above.
- **Trim is a playback window**, not a re-encode: sounds play their selected slice with
  sample-accurate clipping. Pack exports preserve trim metadata, but the underlying file
  stays whole (no ffmpeg in the app).
- **Overlay visibility** can be restricted by some games' anti-overlay/anti-cheat flags
  (e.g. during tournaments or on screens flagged secure). That is the game's decision; the
  overlay simply follows Android's public window rules.
- **Music playlist** = every sound categorized as *Music*. Tag tracks via Edit → Category.

## 📄 License

MIT — see [LICENSE](LICENSE). Bundled starter sounds are synthesized tones generated by
this project (no third-party copyrighted audio).
