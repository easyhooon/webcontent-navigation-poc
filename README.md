# WebContent Navigation PoC

Compares a plain `android.webkit.WebView` with `androidx.web` `WebContent` (`androidx.web:web:1.0.0-alpha01`) when a WebView screen leaves and re-enters composition through Navigation Compose.

## Setup

- One Activity with a `NavHost`: `home`, `plain`, `content`, `other`.
- `plain`: `AndroidView` creates a `WebView`, and `onRelease` calls `destroy()`.
- `content`: a `ViewModel` scoped to the destination's `NavBackStackEntry` holds `WebContent`.
  - `factory` calls `attach(context) { WebContentView(it) }`.
  - `onRelease` removes the view from its parent and calls `detach()`.
  - `onCleared()` calls `close()`.
- The page logs lifecycle events, a load timestamp, a random mark, and a ticker (+1 every 200 ms) to logcat (`NavTest`).

Everything is in [`MainActivity.kt`](app/src/main/kotlin/com/example/navtest/MainActivity.kt).

## Run

```bash
./gradlew :app:installDebug
adb logcat -c
adb shell am start -n com.example.navtest/.MainActivity --es scenario plain
adb shell am start -n com.example.navtest/.MainActivity --es scenario content
adb logcat -s NavTest:I
```

Each scenario runs automatically:

1. Open the web screen.
2. Navigate to `other` and stay there for 5 seconds.
3. Navigate back to the web screen.
4. Navigate back to `home`, which pops the web destination.

## Result

Device: Samsung SM-F916N, Android 13, Android System WebView 157.0.8103.50.

| Step | `WebView` | `WebContent` |
|---|---|---|
| Navigate to `other` | `onRelease` → `destroy()` | `onRelease` → `detach()`, page gets `visibilitychange` (hidden) |
| While on `other` | Page is gone | Page keeps running, but timers are throttled. The ticker went 22 → 26 in about 4 s instead of about 20 |
| Navigate back | New `WebView`, page reloads (`DOMContentLoaded`, `load`, `pageshow`, new mark, ticker 0) | `attach reused=true`, no reload. Same mark and load time, ticker continues. Page gets `visibilitychange` (visible) and `resize` |
| Pop the destination | `destroy()` | `detach()` then `close()` from `ViewModel.onCleared()` |

Log excerpt (`content`):

```
[content] attach reused=false
[content] JS load loadedAt=3:36:19 mark=mark-1795070179129 tick=0 vis=visible
=== navigate -> other (stay 5s)
[content] detach
[content] JS document.visibilitychange ... tick=22 vis=hidden size=0x0
=== back -> content
[content] attach reused=true
[content] JS document.visibilitychange loadedAt=3:36:19 mark=mark-1795070179129 tick=26 vis=visible
[content] JS resize ... tick=27 vis=visible
=== back -> home (content popped)
[content] detach
[content] close (ViewModel cleared)
```

Related: [webcontent-jsdialog-repro](https://github.com/easyhooon/webcontent-jsdialog-repro)
