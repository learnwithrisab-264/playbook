# PlayBook

A distraction-free, ad-free YouTube client for studying, built in Kotlin + Jetpack Compose.
Designed & built by Sourabh.

## Open and run

1. Install **Android Studio** (latest).
2. Unzip this folder and choose **File › Open** on it. Let Gradle sync (first sync downloads a lot).
3. Plug in a phone with USB debugging on and press **Run**, or build an APK with
   **Build › Generate APKs**. Use the **release** variant for smooth playback
   (it is signed with the debug key, so no keystore is needed).

Requirements: Android 11+ phone (minSdk 30). Android Studio creates `local.properties` with your SDK path automatically.

## Make it your own (do this first)

Change the app id so your build is a separate app and never clashes with the original:

- `app/build.gradle.kts` → `applicationId = "com.yourname.ytosmium"`
- `app/src/main/AndroidManifest.xml` → the provider `android:authorities="com.sourabh.jarvistube.study"`
  → e.g. `com.yourname.ytosmium.study` (it must be unique on a phone)
- App name: `android:label` in the manifest. Logo: `res/drawable/ic_osmium_*.xml` and `osmium_badge.xml`.

The Kotlin package (`com.sourabh.jarvistube`) can stay as it is; only `applicationId` decides which app it is.

## Where things are

| Area | Files |
|---|---|
| App start, shared objects | `JarvisTubeApp.kt` (`Graph`), `MainActivity.kt` (tabs, bottom bar) |
| Player | `player/PlayerController.kt` (ExoPlayer, quality, captions, queue), `ui/PlayerScreen.kt`, `ui/PlayerMoreSheet.kt`, `ui/SeekBar.kt`, `ui/SeekPreview.kt` |
| YouTube data | `data/Repo.kt` (NewPipeExtractor), `data/Channels.kt`, `data/InnerTube.kt` (signed-in API), `data/LibraryParser.kt` |
| Sign-in & accounts | `ui/LoginScreen.kt`, `data/Session.kt`, `ui/AccountUi.kt` |
| Study / focus locks | `StudyState.kt`, `ui/StudyScreen.kt`, `data/TrustedClock.kt` (tamper-proof time) |
| Content filter | `data/ContentFilter.kt` |
| Downloads | `data/Downloads.kt`, `ui/DownloadSheet.kt`, `ui/SavedScreen.kt` |
| Upload notifications | `data/UploadAlerts.kt` |
| Theme | `ui/theme/` |

## Notes

- Video data comes from [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor); if YouTube changes
  and videos stop loading, update its version in `gradle/libs.versions.toml`.
- Sign-in is unofficial (WebView cookies); a secondary Google account is safest.
- Personal project, not affiliated with YouTube or Google.
