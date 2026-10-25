# MediaGrab

An on-device Android media downloader built on yt-dlp via the youtubedl-android library.
No backend server: yt-dlp, Python and FFmpeg run inside the app.

## Features (v0.3)
- **Parallel downloads:** 1 to 5 at once (Settings -> Speed), one summary notification with "Cancel all"
- **Size before you download:** every quality shows an estimated file size (video + audio), plus a low-storage warning
- **Built-in browser:** open a site, find a video, tap **Download**; start page with site shortcuts
- Paste a link, or **Share -> MediaGrab** from any app
- Queue + history (survives restarts), retry, cancel, open, delete
- Foreground service: downloads continue when the app is closed
- Faster on streaming sites: parallel fragment download ("Fast mode")
- Compatible-files mode (MP4/M4A) so downloads play on every phone and TV
- Settings: default quality, theme, system colours, save folder, metadata, Wi-Fi only, engine update
- Brand theme, original illustrations, adaptive app icon with Android 13 themed icon

## Open and run
1. Android Studio (Ladybug or newer): *File -> Open* this folder, accept the Gradle prompt.
2. In `app/build.gradle.kts` confirm `ytdlVersion` and the library coordinates on the youtubedl-android GitHub page.
3. Plug in a phone (USB debugging on) and press **Run**.

If Android Studio shows a red error on `fileSize`, `fileSizeApproximate`, `tbr`, `ext` or `vcodec` in
`DownloadRepository.kt`, open the library's `VideoFormat` class and use the exact property names.

## Build an APK
- GitHub: push the project; **Actions -> Build APK** produces debug APKs as an artifact.
- Signed release: *Build -> Generate Signed App Bundle / APK*. See `PUBLISHING.md`.

## Code map
| File | Job |
|---|---|
| `MediaGrabApp.kt` | init settings, queue, channels, engine |
| `DownloadRepository.kt` | yt-dlp: info + size estimates, download, MediaStore save, engine update |
| `DownloadCenter.kt` | queue + history state (persisted) |
| `DownloadService.kt` | foreground service, parallel workers, notification |
| `BrowserController.kt` | WebView that survives tab switches |
| `Settings.kt` | preferences |
| `MainViewModel.kt` | UI state |
| `ui/*` | Theme, Home, Browser, DownloadSheet, Downloads, Settings, navigation |

## Licences
yt-dlp: Unlicense. youtubedl-android: GPL-3.0 (verify). FFmpeg: LGPL/GPL. Read `PUBLISHING.md` before releasing.
Download only content you own or have permission to save.

## Signed release via GitHub (professional way)
See "Release steps" in the chat guide: create a keystore once, store it as 4 GitHub Secrets
(KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD), then push a tag like `v0.3.0`.
The `Release APK` workflow builds, signs and publishes the APKs to GitHub Releases.
