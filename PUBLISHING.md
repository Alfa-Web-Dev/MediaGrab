# Publishing checklist (read before you release to the public)

This is practical guidance, not legal advice. Talk to a lawyer before a large public launch.

## 1. Where you can and cannot distribute
| Channel | Reality for a downloader that supports YouTube and similar sites |
|---|---|
| Google Play | Will almost certainly be rejected or removed. Repeated violations can ban the developer account. |
| F-Droid / IzzyOnDroid | Possible for fully open-source apps with no proprietary ad/analytics SDKs. Review rules apply. |
| GitHub Releases | Works well. Add Obtainium or in-app update checks. |
| Your own website (APK download) | Works. You are responsible for hosting, signing and support. |

## 2. Licence (important)
- youtubedl-android is GPL-3.0 (verify in its repo). Shipping it means your app must be GPL-3.0 and you must
  provide the full source. Add a `LICENSE` file (GPL-3.0 text) and keep copyright notices.
- yt-dlp (Unlicense) and FFmpeg (LGPL/GPL, check the build you ship) have their own terms.
- Proprietary SDKs (AdMob, Firebase Analytics, etc.) generally cannot be combined with GPL code.
  If you want ads, get legal advice first. Ad networks also restrict apps that violate other sites' terms.

## 3. Money without ad-network risk
Donations (GitHub Sponsors, Ko-fi, UPI link), a paid "supporter" build sold on your own site, or a one-time unlock.

## 4. Legal and trust
- Keep the on-screen notice: download only content you own or have permission to save.
- Do not use other companies' names or logos as your app name or icon. (Only YouTube-style generic words in text are fine.)
- Publish a Privacy Policy (`PRIVACY.md` is a template) and a Terms/Disclaimer page on a public URL.
- Be ready for takedown / DMCA notices: have a contact email and a response process.

## 5. Quality bar for "lakhs of users"
- [ ] Test on 8+ real devices (Android 10 to 15, low RAM phones, Samsung/Xiaomi/Oppo battery savers).
- [ ] Add crash reporting that fits your licence choice (self-hosted Sentry/GlitchTip, or ACRA).
- [ ] In-app update check (GitHub Releases API) so users get new yt-dlp-compatible versions fast.
- [ ] Auto-update the engine in the background (once a day, Wi-Fi only).
- [ ] Release signing with a keystore stored safely (back it up; losing it means users cannot update).
- [ ] Turn on code shrinking (`isMinifyEnabled = true`) and test every feature again.
- [ ] Unit tests for URL handling and size estimation; a small UI test for the download flow.
- [ ] Playlist support, subtitles, resume after reboot, speed limit (planned features).
- [ ] Localisation (Hindi first), accessibility labels, tablet layout.
- [ ] Server costs: none for downloading. You pay only for the website/update hosting you choose.

## 6. Build a signed release
1. Android Studio -> Build -> Generate Signed App Bundle / APK -> APK -> create keystore.
2. Upload the `app-arm64-v8a-release.apk` (small) and the universal APK to GitHub Releases.
3. Write release notes. Increase `versionCode` for every release.
