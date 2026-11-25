# XC TV Wall — original design

> The design notes the first version was built from (November 2026). The project started as an
> onboarding slideshow for an office; the app itself plays any Drive folder. Some details have
> changed since: the slide time is now set per TV from the remote, and the app id is
> `tech.xelerate.tvwall.player`. See the [README](../README.md) for current behaviour.

## Context
When a new employee joins, the team wants the office TVs (Hisense + Fire TV Stick 4K / 4K Max) to loop
welcome messages from the team and the new joiner's self-presentation. Content is already made by
people (images, Google Slides decks, videos) and dropped into a Google Drive folder. Constraint: no
subscription. Chosen approach (chosen: **C**): a custom, sideloaded Fire TV app that privately syncs
the Drive folder, caches it locally and plays it full-screen in a loop, offline-tolerant.

## Approaches considered
- **A. Apps Script web page + browser/launcher** — no hosting, but videos must be "anyone with link",
  needs Wi-Fi, and a clean full-screen still needs a sideloaded kiosk/launcher.
- **B. Static page + Drive API key** — whole folder must be public-by-link; weak Slides support.
- **C. Custom Fire TV app (chosen)** — private Drive access, smooth local video playback, survives Wi-Fi
  drops. Cost: Android build + one-time sideload per stick. No store publishing needed.

## Goals / non-goals
- Goals: zero-touch playback after setup; editing the Drive folder updates every TV within minutes;
  no controls/UI on screen; screen never sleeps while playing; starts on boot.
- Non-goals: scheduling, per-TV playlists, a management UI, Slides animations/transitions, audio mixing.
- Anti-goals: a public Drive folder; a stuck black screen when Wi-Fi or Google is unavailable.

## Design (calls marked ★ are mine — easy to change)
**Drive access — service account.** ★ Google's TV sign-in flow can't grant read access to arbitrary
Drive files, so the app uses a **service account** (free Google Cloud project, Drive API enabled). The
folder is shared with the service-account email as *Viewer* — nothing becomes public. The key JSON is
kept out of git (`secrets/service-account.json`) and bundled at build time; it can read only what's
shared with it.

**Folder rules.** ★
- Play order = file name, A→Z (people use `01_`, `02_` … prefixes).
- Files/folders starting with `_` are ignored (scratch space, `_app/` for updates).
- Images (JPG/PNG/WebP): shown **10 s**. Google Slides: exported to PDF, each slide shown **10 s**.
  Videos (MP4/MOV/WebM): play to the end, with sound.
- Optional per-file override via a suffix in the name, e.g. `03_welcome__20s.png`.

**Sync.** Every **5 min** ★ while running: list folder → compare against the local manifest
(fileId + modifiedTime) → download new/changed files into a staging dir → after a fully successful sync,
swap the manifest atomically and delete removed files. If a sync fails, keep playing the last good set.
On first run with no cache: show a simple "Waiting for content…" screen with the folder name.

**Playback.** A single full-screen activity (immersive, `FLAG_KEEP_SCREEN_ON`, no focusable UI).
Images use crossfades; Slides PDF pages are rendered with Android `PdfRenderer` at 1920×1080;
videos use Media3 ExoPlayer. Playlist changes apply at the next loop boundary so the current item never jumps.

**Startup.** Boot receiver + a `HOME`-independent relaunch. Fire OS 8 limits starting apps from the
background, so setup grants `SYSTEM_ALERT_WINDOW` via adb (one command) so autostart works.

**Self-update.** ★ The app checks `_app/` in the folder for `xc-tv-wall-<versionCode>.apk`; if newer,
it downloads it and opens the system installer (someone presses *Install* once with the remote).
Store publishing isn't needed.

## Implementation plan 
Stack: Kotlin, Gradle (wrapper), minSdk 25 / targetSdk 34, Media3 ExoPlayer,
`google-api-services-drive` + `google-auth-library-oauth2-http`, coroutines. The Android SDK already exists at
`~/Library/Android/sdk` (JDK 17 present; `adb` is in `platform-tools`, not on PATH).

1. **Scaffold** — `git init`, Gradle wrapper, single `app` module, Leanback launcher manifest entry
   + TV banner, `.gitignore` incl. `secrets/`. Save this design as `docs/design.md`.
2. **Folder rules (pure Kotlin, TDD)** — `PlaylistRules`: name sorting, `_` ignore, MIME → item type,
   `__Ns` duration parsing. Unit tests.
3. **Sync engine (TDD with fake Drive)** — `DriveSource` interface + `ServiceAccountDriveSource`;
   `SyncEngine` diff/staging/atomic-swap/cleanup; Slides → PDF export; manifest JSON. Unit tests
   for diffing, failure keeps old set, removal cleanup.
4. **Player** — `PlayerActivity` driving a queue: `ImageSlide`, `PdfPageSlide`, `VideoSlide`;
   crossfade; keep-screen-on; waiting screen; loop-boundary playlist swap.
5. **Background loop + boot** — periodic sync coroutine, `BootReceiver`, relaunch handling.
6. **Self-update** — `_app/` APK check, download, installer intent (`REQUEST_INSTALL_PACKAGES`).
7. **Setup docs** — `README.md`: Google Cloud/service-account steps, sharing the folder, folder rules
   for the team, Fire TV dev-mode + sideload (Downloader app or `adb install`), the one adb permission command.

## Verification
- `./gradlew test` — rules + sync engine unit tests pass.
- `./gradlew assembleDebug`, install on an Android TV emulator (1080p) via adb with a test folder
  shared to the service account: confirm images/Slides/video play in order, full-screen, with no UI.
- Edit the folder (add, rename, delete) → change appears within ~5 min with no visual glitch.
- Disable network → keeps looping the cached content; re-enable → resyncs.
- On a real Fire TV Stick 4K: sideload, reboot the stick → app starts by itself; leave it 30+ min → no screensaver.

## Needed from you during build
- Create the Google Cloud project + service account (README walks through it, ~10 min) and share the
  Drive folder with its email.
- The Fire TV's IP address (or use Downloader) for the first install.
