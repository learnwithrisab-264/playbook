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
| App start, shared objects | `JarvisTubeApp.kt` (`Graph`), `MainActivity.kt` (tabs, bottom bar, PiP) |
| Screens, by feature | `ui/home`, `ui/search`, `ui/shorts`, `ui/you`, `ui/channel`, `ui/player`, `ui/study`, `ui/downloads` |
| Shared screen parts | `ui/common`, `ui/theme` |
| Player engine | `player/` (ExoPlayer, background media service) |
| YouTube data, saved lists | `data/` |
| Study / focus locks | `StudyState.kt`, `lock/` (phone-wide Lock Study Session), `ui/study`, `data/TrustedClock.kt` |
| Feeds and library state | `TubeViewModel.kt`, `LibraryViewModel.kt` |

Don't move `lock/StudyGuardService`, `player/PlaybackService` or `data/UploadAlerts`: Android stores their class
names, so moving them turns off the accessibility permission or scheduled upload checks on installed phones.

Old instruction notes are in `docs/`.
## Notes

- Video data comes from [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor); if YouTube changes
  and videos stop loading, update its version in `gradle/libs.versions.toml`.
- Sign-in is unofficial (WebView cookies); a secondary Google account is safest.
- Personal project, not affiliated with YouTube or Google.
