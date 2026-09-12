# Game Sidebar - Floating Browser (Master Bugfix V2)

Existing Android project - **SAFE TARGETED PATCH**, not rebuilt.

## Quick Start

1. Grant overlay permission: App will prompt for `SYSTEM_ALERT_WINDOW`.
2. Tap **Open Sidebar** - appears over Free Fire.
3. Drag only via header (40dp), resize via 16dp edges, scroll webpage inside WebView.
4. Double-tap outside sidebar (300ms) → collapse to `[◀]` handle. Tap handle → reopen same page/size.
5. Address bar: type → press GO → keyboard hides, sidebar stays.

## Settings
- `Auto-hide controls during video` (default ON) - hides title/tabs/address/shortcuts on video, leaves video area max.

## Login
If site blocks embedded login: banner "এই সাইটটি নিরাপত্তার কারণে embedded browser login অনুমতি দিচ্ছে না।" + [Secure Login] → opens CustomTabs, no password stored.

## Build
```bash
git am patches/0001-Game-SideBar-V2-keyboard-Back-double-tap-collapse-vi.patch
git checkout main && git merge feature/game-sidebar-v2
./gradlew assembleDebug  # APK: app/build/outputs/apk/debug/app-debug.apk
```
CI: `.github/workflows/build-apk.yml` does same + uploads artifact.

## Architecture
Preserved: `OverlayManager`, `SidebarPanelView`, `BrowserController`, `WebViewFactory`, tabs/shortcuts/drag/resize/overlay.

Fixed via helpers: `CustomWebView`, `VideoModeManager`, `CollapseHandleView`, `OutsideDoubleTapDetector`, `LandscapeHelper`, `PrefsManager`.

See `FINAL_REPORT.md` for root causes, file list, test results, APK path.
