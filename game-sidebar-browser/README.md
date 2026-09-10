# Game SideBar Browser

A floating sidebar browser for Android that stays on top of your game. A small pill-shaped handle
lives at the edge of the screen; tap it and a glass panel slides out with a real browser, quick
site shortcuts and a handful of offline tools. Close it and you are back in the game - no home
screen, no context switch.

Native Kotlin, Jetpack Compose for the in-app screens, `WindowManager` + Views for the overlay,
WebView for the web. No Firebase, no accounts, no paid APIs, no tracking. The app is free and works
offline except for the web content you load.

---

## Contents

- [What you get](#what-you-get)
- [Project layout](#project-layout)
- [Build and run](#build-and-run)
- [Offline verification](#offline-verification)
- [Architecture](#architecture)
- [Permissions](#permissions)
- [Design decisions and platform limits](#design-decisions-and-platform-limits)

---

## What you get

**Floating handle**
- White rounded pill, three sizes (56x6, 74x8 or 90x10 dp), soft glow drawn as three
  translucent layers (not a `Paint` shadow layer, which is unreliable under hardware
  acceleration).
- Tap opens the panel, double tap toggles it, long press enters drag mode, drag moves it
  vertically or freely, release snaps it to the nearest edge.
- Position, size and edge are persisted (DataStore) and restored on the next start.
- Edge snap and partial off-screen hide are both settings (`Auto snap`, `Edge hide mode`).

**Floating panel**
- Opens with a spring/fade/scale animation anchored on the handle, 260 ms
  `OvershootInterpolator`, closing in 160 ms. Animations are skipped in Gaming Mode.
- Size presets Small / Medium / Large, manual resize from the corner grip, nine anchor positions
  (Top / Middle / Bottom x Left / Center / Right) plus free drag from the header.
- Opacity 20-100 % (default 90 %), glow on/off with Low / Medium / High and five colours.
- Tap outside to close, swipe down on the header to minimise, swipe left/right on the tab strip to
  switch tabs.

**Mini browser**
- Address bar that distinguishes URLs from search text (`UrlResolver`), back / forward / reload /
  home / stop, progress bar, favicon-or-letter tab badges, tab close, new tab.
- JavaScript, cookies, DOM storage, desktop/mobile user-agent switch, HTML5 fullscreen video.
- Bookmarks (Room), history (Room, grouped by day, delete one or clear all, plus a
  "Don't save history" switch), search engine choice: Google (default), Bing, DuckDuckGo.
- Downloads go through `DownloadManager` behind a Download/Cancel prompt, with a progress
  notification owned by the system.

**Quick tools**
- Browser, YouTube, Google search, a real expression calculator (`2 + 3 x 4 - 20% = 13`),
  notes (create / edit / delete / search / pin), a stopwatch and countdown timer, a clipboard
  viewer, brightness and volume shortcuts.

**Service and gaming mode**
- Foreground service with an ongoing notification: "Game SideBar is active" and Open Sidebar /
  Hide Sidebar / Stop Service actions.
- Gaming Mode cuts animations and glow and tears the panel view down aggressively to keep the
  WebView's memory footprint low while you play.

**Privacy**
- Incognito: nothing written to history, cookies cleared on stop.
- Never stores passwords. Google/YouTube sign-in that WebView cannot complete is handed to a
  Custom Tab, which returns to the app when finished.

---

## Project layout

```
game-sidebar-browser/
├── settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
├── core/                                  pure Kotlin/JVM, zero Android imports
│   └── src/main/kotlin/com/gamesidebar/core/
│       ├── browser/    UrlResolver, Tabs, Shortcuts, ShortcutCodec
│       ├── calc/       Calculator (shunting-yard + postfix evaluation)
│       ├── data/       Bookmarks, History, Notes, Clipboard models + rules
│       ├── download/   Downloads (mime map, filename sanitising)
│       ├── geometry/   OverlayGeometry (dp, handle snap, panel placement)
│       ├── model/      AppSettings and the value types it is built from
│       ├── security/   UrlSafety (scheme policy, SSL error keys)
│       ├── service/    OverlayCommand
│       ├── timer/      TimerState
│       └── util/       Formatting
│   └── src/test/kotlin/...   141 tests, plain JVM, no test framework needed
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/        66 vector drawables, layouts, themes, strings (no emojis, no bitmaps)
│       └── kotlin/com/gamesidebar/browser/
│           ├── MainActivity.kt, BrowserActivity.kt, GameSidebarApplication.kt
│           ├── browser/     WebViewFactory, BrowserController, WebView/WebChrome clients,
│           │                DownloadCoordinator
│           ├── data/        Room (entities, DAOs, AppDatabase), DataStore SettingsRepository,
│           │                BrowserDataRepository, ServiceLocator
│           ├── overlay/     OverlayService, OverlayManager, FloatingHandleView,
│           │                SidebarPanelView, PanelTools, ClipboardHistory
│           ├── ui/          Compose: home, browser, bookmarks, history, notes, settings,
│           │                theme, glass components
│           └── util/        OverlayNotification, OverlayPermission, ScreenMetrics,
│                            ExternalBrowser
└── tools/
    ├── android-stubs/   a mirror of the Android/AndroidX/Compose APIs this app uses
    ├── gen_r.py         generates R.kt from res/ so every resource reference is checked
    ├── run_core_tests.sh, check_app.sh, verify_all.sh
```

---

## Build and run

Requirements: Android Studio (Ladybug or newer), JDK 17, Android SDK 35.

```bash
cd game-sidebar-browser
./gradlew assembleDebug          # or open the folder in Android Studio and press Run
```

`compileSdk` / `targetSdk` 35, `minSdk` 26, Kotlin 2.0.20, AGP 8.7.3, KSP for Room.

The first build needs network access to resolve AGP, AndroidX and Compose from Google's Maven
repository.

---

## Offline verification

Two scripts verify the project without an Android SDK, a Gradle download or a network:

```bash
tools/run_core_tests.sh     # compiles :core and runs its 141 tests as a plain JVM program
tools/check_app.sh          # compiles the whole :app module against tools/android-stubs
tools/verify_all.sh         # both
```

`check_app.sh` also runs `gen_r.py`, which generates the `R` class from `res/` itself, so a
reference to a string, drawable or `@+id` that does not exist is a compile error rather than an
AAPT2 failure later.

What this does **not** cover, and why it matters:
- `./gradlew assembleDebug` - needs the Android SDK and Maven. AGP, AAPT2, KSP/Room code
  generation and R8 are not exercised here.
- The Compose compiler plugin is not applied (it needs the real `androidx.compose` artifacts), so
  `@Composable` call-site rules are unverified. Everything else - unknown identifiers, wrong
  signatures, type mismatches, missing imports, missing resources - is caught.
- Runtime behaviour of overlay windows, WebView and the foreground service needs a device.

---

## Architecture

```
UI (Compose screens)      Overlay (Views)
        \                   /
         ViewModel / Host  /
              \          /
        Repositories  BrowserController
         /     |      \        |
   Room     DataStore  WebView  DownloadManager
```

- **`core`** holds every rule that is not Android: URL routing, tab state, geometry, the
  calculator, timer transitions, download filename handling, the URL safety policy. It has no
  Android imports, so it is unit-testable on a plain JVM - 141 tests, ~300 ms.
- **Repositories** are the only writers. `SettingsRepository` owns DataStore,
  `BrowserDataRepository` owns Room, both expose `Flow`s that the UI collects.
- **`BrowserController`** owns the single WebView instance and is the only class that talks to it.
  Both the overlay panel and the in-app browser screen drive it through the same
  `BrowserController.Listener` contract, so the two surfaces behave identically.
- **`OverlayManager`** owns the two overlay windows (handle and panel), the drag/snap logic and the
  fullscreen re-parenting. It is created and destroyed with `OverlayService`.
- **`OverlayService`** is a `specialUse` foreground service that dispatches `OverlayCommand`s.

The overlay deliberately uses Views + XML while the in-app screens use Compose: an overlay window
needs direct control over `LayoutParams`, focus flags and re-parenting, and pulling the Compose
runtime into a service-hosted window costs memory that a game is already asking for.

---

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Loading pages, detecting "no network" for the error state |
| `SYSTEM_ALERT_WINDOW` | The floating handle and panel |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Staying alive while you play |
| `POST_NOTIFICATIONS` | The ongoing service notification (Android 13+) |

The overlay permission is explained on first launch and is optional: without it the app is still a
complete browser, and `BrowserActivity` is registered as a VIEW handler for http/https so
"Open with Game SideBar" works from other apps.

The service notification is always visible while the service runs. It is never hidden - a hidden
foreground-service notification is both a Play policy violation and a lie to the user.

---

## Design decisions and platform limits

**YouTube and sign-in.** YouTube plays in the WebView (fullscreen video is supported by growing the
panel window and re-parenting the WebView's custom view). Google sign-in inside a WebView is
blocked by Google's own policy, and this app does not try to work around it. When the page asks for
a login the app offers "Open login in browser" via Custom Tabs and returns afterwards. Passwords
are never captured, stored or autofilled by this app.

**DRM, anti-cheat, game traffic.** Nothing here injects into games, reads game memory, touches
anti-cheat, or intercepts protected traffic. The overlay is a normal window with a normal WebView.

**Downloads.** Handled by the system `DownloadManager` after a confirmation prompt, into the public
Downloads directory on Android 10+ and the app-specific external directory below that - so no
storage permission is required at any API level.

**Clipboard.** Modern Android only hands the clipboard to the focused app, so the clipboard tool
records items when you tap a button inside the panel rather than polling in the background. Clips
marked sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`, API 33+) are refused.

**Brightness.** The brightness slider adjusts the overlay window's `screenBrightness`, which is the
only brightness control an app may use without `WRITE_SETTINGS`. There is no screenshot shortcut -
that requires either `MediaProjection` consent or accessibility privileges, neither of which belongs
in a browser.

**WebView is optional.** If the WebView provider is missing or disabled the app still runs: it says
so, and offers the system browser instead of crashing.

**Every failure path has a screen**: no network, page failed, blocked scheme, blocked file access,
SSL error, empty URL, download failed, WebView missing, overlay permission revoked, service
restricted. None of them throw.

---

## Licence

Apache 2.0 for the code in this repository. Bundled dependencies keep their own licences.
