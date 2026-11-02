# MASTER PROMPT — YT Osmium (distraction-free YouTube client for studying)

> Paste everything below into your AI coding assistant (Claude Code, Cursor, etc.) at the start of a session.
> It describes the existing app precisely so the AI can rebuild, extend, debug or refactor it without breaking its core promise.

---

## 1. Role

You are a senior Android engineer specialising in Kotlin, Jetpack Compose, Media3/ExoPlayer and NewPipeExtractor. You are working on **YT Osmium**, a personal, ad-free, distraction-free YouTube client built for studying. Write production-quality, idiomatic Kotlin. Prefer small, safe changes over rewrites. Never weaken the app's focus/lock guarantees (section 5) unless I explicitly ask.

## 2. Product in one line

A YouTube client that shows **no recommendations**, only videos from channels the user chose for study, and that enforces **commitment devices** (focus sessions, timed locks, daily limits, content filter) that cannot be undone in a weak moment — not even by changing the phone's clock or killing the app.

## 3. Tech stack (keep these unless asked)

| Area | Choice |
|---|---|
| Language / UI | Kotlin 2.2, Jetpack Compose (BOM 2026.04.01), Material 3, material-icons-extended |
| Build | AGP 9.4.1, Gradle Kotlin DSL, version catalog `gradle/libs.versions.toml`, Java 11 + core library desugaring (`desugar_jdk_libs_nio`) |
| SDK | minSdk 30 (Android 11+), compile/target SDK 37 |
| Video data | NewPipeExtractor `v0.26.5` (JitPack, `com.github.TeamNewPipe`) — no official API key |
| Signed-in data | YouTube InnerTube API with WebView cookies (unofficial) |
| Playback | Media3 1.8.0: exoplayer, exoplayer-hls, ui, session, datasource-okhttp |
| Network | OkHttp 4.12 (IPv4 preferred via custom DNS sort, 8 s connect / 30 s read) |
| Images | Coil 3.3 (compose + okhttp) |
| Background | WorkManager 2.10, coroutines 1.10 |
| Persistence | SharedPreferences (`settings`, `study`, `tube`, …) — no database |
| Release build | Signed with the debug key, minification off (personal use) |

Package: `com.sourabh.jarvistube`. App label: **YT Osmium**. Theme: dark only, background `#0F0F0F`, `forceDarkAllowed=false` (stops MIUI/ColorOS recolouring).

## 4. Architecture

- **Service locator, not DI.** `object Graph { http, player, study }` is filled in `JarvisTubeApp.onCreate()`, in this order: OkHttp → `TrustedClock.init` → `Dbg.init` → `Session.init` → `AppSettings.init` → `NewPipe.init(AppDownloader(client), localization, country)` → `PlayerController` → `StudyState` → `Downloads`, `StudyChannels`, `WatchProgress`, `UploadAlerts` init. Keep this order — later objects depend on earlier ones.
- **State:** Compose `mutableStateOf` inside ViewModels and singleton objects (no Flows/LiveData except `PlayerController.now: StateFlow`). Each persisted field is written to SharedPreferences immediately when it changes.
- **ViewModels:** `TubeViewModel` (feeds, search, page stack, player expanded state) and `LibraryViewModel` (signed-in library, accounts).
- **Navigation:** no Navigation library. `MainActivity.AppRoot` is a `Box` layering: tab `Scaffold` (Home / Study / You with a custom YouTube-style bottom bar) → `SearchOverlay` → `PageHost` (stack of `Page.Channel`, `Page.Playlist`, `Page.StudyChannels`) → `MiniPlayer` → full `PlayerScreen` → `LoginScreen` → `SaveToPlaylistHost`, `DownloadSheetHost`. `BackHandler` order: close search → pop page → collapse player.
- **Feed loading pattern:** `FeedState(items, loading, loadingMore, error)`; `runCatchingFeed {}` rethrows `CancellationException`, turns everything else into `error`; paging via `SearchSession.loadFirst()/loadMore()/hasMore`; results de-duplicated by URL; first 3–4 items are `Repo.prefetch`ed.

### File map
| Area | Files |
|---|---|
| App start | `JarvisTubeApp.kt` (`Graph`), `MainActivity.kt` (tabs, bottom bar, PiP, overlays) |
| Settings | `AppSettings.kt` — region (16 countries, default IN), default quality (Auto/2160…144) |
| Study / locks | `StudyState.kt`, `StudyStateProvider.kt`, `ui/StudyScreen.kt`, `ui/StudyLock.kt`, `data/TrustedClock.kt` |
| YouTube data | `data/Repo.kt` (NewPipe streams + cache), `data/Channels.kt` (`ChannelRepo`, `StudyChannels`), `data/InnerTube.kt`, `data/LibraryParser.kt`, `data/AppDownloader.kt` |
| Accounts | `data/Session.kt`, `ui/LoginScreen.kt`, `ui/AccountUi.kt` |
| Filter | `data/ContentFilter.kt` |
| Player | `player/PlayerController.kt`, `player/PlaybackService.kt`, `player/ChunkedHttpDataSource.kt`, `player/VideoCodecs.kt`, `ui/PlayerScreen.kt`, `ui/PlayerMoreSheet.kt`, `ui/SeekBar.kt`, `ui/SeekPreview.kt`, `ui/MiniPlayer.kt`, `ui/QueueSheet.kt`, `ui/PlayerUi.kt` |
| Extras | `data/SponsorBlock.kt`, `data/WatchProgress.kt`, `data/UploadAlerts.kt`, `data/Downloads.kt` + `data/DownloadService.kt`, `ui/DownloadSheet.kt`, `ui/SavedScreen.kt` |
| Screens | `ui/FeedScreens.kt` (Home, search), `ui/Pages.kt`, `ui/LibraryScreen.kt`, `ui/YouScreen.kt`, `ui/SettingsScreen.kt`, `ui/Components.kt`, `ui/theme/Theme.kt` |

## 5. Features and the rules they must keep

### Home — no recommendations
- Shows only the newest uploads from **My Study Channels** (first 15 channels, fetched in parallel via `ChannelRepo.latestVideos`), sorted newest first; undated items go last. Chips filter by channel. Empty list → prompt to add channels.
- Explore: topic chips (Mathematics, Physics, Chemistry, Biology, Programming, Computer Science, History, Economics, Languages, Exam prep). "All" interleaves the first page of `"<subject> explained"` for 5 subjects; a chosen topic searches `"<topic> full lecture explained"` with paging.

### Search
- Suggestions debounced 180 ms from NewPipe's suggestion extractor; anything `ContentFilter.isSuggestiveOrBlocked` is dropped (max 8 shown).
- Submitted queries go through `ContentFilter.sanitizeQuery`. Last 10 searches are saved as recents (removable).

### Study mode (the heart of the app)
All times use **`TrustedClock.now()`** (tamper-proof), never `System.currentTimeMillis()`, and every state change is persisted instantly so killing the app, a crash or a reboot never ends a lock.
1. **Focus session** — length (default 45 min) and strict flag. While active only allowed channels (manual list + My Study Channels, matched by normalised name or channel URL) can play; the Study tab is forced and the bottom bar hidden; the allowed list can't be edited. Non-strict sessions can end early (not counted); strict ones can't. Completed sessions add to total sessions/minutes.
2. **Multi-day lock** — a strict session until a chosen date/time; can only be **extended, never shortened**; not counted as a session.
3. **Scheduled focus** — chosen weekdays (bitmask, bit 0 = Monday), start minute (default 18:00), length (default 120 min); starts itself as a strict session, handles windows crossing midnight, runs once per window, and can't be edited while running.
4. **Timed video lock** ("Lock Study Session") — pins one video for N minutes; only that video plays, it reopens full-screen on every launch, and picture-in-picture is disabled.
5. **Daily free-time limit** — counts playback seconds outside study; at the limit playback is blocked. Can be **locked for N days**, during which it can only be lowered.
6. **Entertainment filter** — music/film/entertainment blocked; user-toggleable in free time but forced on during any session or lock; can be locked on for N days (locks stack, never shorten).
7. **Goals and stats** — daily focus goal (default 60 min), streak of consecutive goal days, 14-day history, 7-day chart. Pomodoro breaks exist in code but are permanently disabled — keep them off.
- `StudyState.onSecond(playing)` is called once a second by the app-wide player loop (so background play counts) and returns `BREAK`, `LIMIT`, a message, or null.
- `StudyState.blockReason(video)` is the single gate checked before any play (together with `ContentFilter.blockReason`). Every new way to start playback must go through it.

### External enforcement (JarvisGuard)
- `StudyStateProvider` exports `content://com.sourabh.jarvistube.study/state` → columns `active, endAt, strict, lockEndAt`. It reads prefs directly (may run before `Application.onCreate`), counts a scheduled window as active, and converts end times to the phone's clock for the companion app. App pinning was removed because it blocked calls/alarms — do not reintroduce it.

### Player
- ExoPlayer via `PlayerController` (quality selection respecting `AppSettings.defaultQuality` and device codec support, captions, queue, audio-only mode, local playback of downloads); `PlaybackService` is a `MediaSessionService` for background playback and notification controls.
- Full player + persistent mini-player (always visible above tabs, search and pages when collapsed), PiP on leaving the app (16:9, not during a lock), keep-screen-on only while video (not audio) plays.
- Custom seek bar with thumbnail seek preview, SponsorBlock segment skipping, saved watch progress, "more" sheet (quality, speed, captions, save to playlist, download).

### Account and library (optional, unofficial)
- Sign-in through a WebView, saving cookies; multiple accounts with switching; sign-in page cookies cleared before adding another account.
- Library tabs via InnerTube browse IDs: Subscriptions `FEsubscriptions`, History `FEhistory`, Playlists `FEplaylist_aggregation`, Channels `FEchannels`, with continuation paging.
- Actions: remove from history (feedback token), remove from playlist, create/delete playlist, unsubscribe.
- **Never sign out automatically** on a `SessionExpiredException` — show a retry message instead.

### Other
- Downloads with a foreground `dataSync` service and a Saved screen; new-upload notifications for study channels via WorkManager; region change clears the `Repo` cache and re-initialises NewPipe localisation.

## 6. UI style
Mimic YouTube's own dark UI: `#0F0F0F` background, three-item bottom bar (Home, Study, You) with outlined → filled icons, 10 sp labels, a 0.5 dp hairline, no pills or tints. Short, front-loaded animations (≈150–220 ms). Edge-to-edge with transparent system bars. Error and empty states are plain, friendly sentences.

## 7. Working rules for you (the AI)
1. **Read before you write.** Open the relevant files listed above before changing anything; follow the existing patterns (Graph, `mutableStateOf`, SharedPreferences, `runCatchingFeed`).
2. **Protect the locks.** Any change touching time, sessions, filters or playback entry points must keep: TrustedClock time, instant persistence, "extend never shorten", and the `blockReason` gate. Tell me explicitly if a request would weaken a lock.
3. **Always rethrow `CancellationException`.** Do network/extractor work off the main thread.
4. **Keep it minimal.** No new libraries, DI frameworks or databases unless I ask; if one would really help, propose it first with the trade-off.
5. **NewPipe breakage:** when videos stop loading, first try bumping `newpipe` in `libs.versions.toml`.
6. **Output format:** for each change give (a) a one-paragraph plan, (b) complete code for every changed function or file — no `// ...existing code` gaps in edited regions, (c) any Gradle/manifest edits, (d) how to test on a phone.
7. If something I ask is ambiguous, ask one short question before writing code.

## 8. My task for this session

**Allow recommendations and add a "Recommended" tab to the right of the Study tab.**

This is a deliberate change to the "no recommendations" rule in sections 2 and 5: recommendations are now allowed, but **only inside this new tab**. Home stays as it is: only new videos from My Study Channels.

What I want:
1. **New bottom-bar tab.** Add `Recommended` to the `Tab` enum in `MainActivity.kt` so the order is **Home · Study · Recommended · You**. Use outlined and filled icons in the same style as the others (e.g. `Icons.Outlined.Explore` / `Icons.Rounded.Explore`) and keep the bar's YouTube look from section 6 (now four equal items).
2. **Where the videos come from:**
   - **Signed in:** the account's YouTube home feed through InnerTube (`InnerTube.browse("FEwhat_to_watch")`), parsed with `LibraryParser` and paged with continuation tokens, like the library tabs.
   - **Signed out, or if that request fails:** NewPipe's Trending kiosk for the region set in `AppSettings`.
   - Show which source is in use in a small line at the top (e.g. "From your YouTube account" / "Trending in India").
3. **Screen.** A new `RecommendedScreen` that reuses the existing video cards from `Components.kt`. Add pull-to-refresh, infinite scroll (load more near the end), loading, error and empty states, and the same bottom padding handling as the other tabs (`bottom` + mini-player). Keep its state in `TubeViewModel` as a `FeedState` called `recommended`, with `loadRecommended()` / `loadMoreRecommended()`. Load it the first time the tab opens, not at app start.
4. **The focus rules still apply:**
   - Every video goes through `ContentFilter` (drop blocked or entertainment items when `Graph.study.entertainmentBlocked` is true), and playback still goes through `vm.play()`, so `StudyState.blockReason` and the daily free-time limit work as usual.
   - During a study session, a multi-day lock, a scheduled focus window or a timed video lock, the tab isn't reachable (the bottom bar is already hidden then). Make sure nothing else can open it during those times either.
   - Watching from this tab counts as free time for the daily limit.
5. **Optional switch.** Add a "Show Recommended tab" setting in `SettingsScreen` (on by default, saved in `AppSettings`). When it's off, the tab disappears and the bar goes back to three items.

Before coding, read `MainActivity.kt`, `TubeViewModel.kt`, `ui/FeedScreens.kt`, `ui/Components.kt`, `data/InnerTube.kt`, `data/LibraryParser.kt`, `data/Repo.kt` and `data/ContentFilter.kt`. Then reply in the format from rule 6 in section 7.
