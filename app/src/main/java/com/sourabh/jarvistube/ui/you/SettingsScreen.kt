package com.sourabh.jarvistube.ui.you

import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.FormatColorFill

import androidx.compose.material.icons.rounded.Tune

import androidx.compose.material.icons.rounded.Fullscreen

import androidx.compose.material.icons.rounded.Apps

import com.sourabh.jarvistube.R
import androidx.compose.ui.res.stringResource
import com.sourabh.jarvistube.ui.theme.Corners
import com.sourabh.jarvistube.ui.common.SectionHeader
import com.sourabh.jarvistube.ui.common.Avatar
import com.sourabh.jarvistube.ui.common.SubPageHeader
import com.sourabh.jarvistube.ui.player.CaptionText
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.AppSettings
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.LibraryViewModel
import com.sourabh.jarvistube.TubeViewModel

@Composable
fun SettingsScreen(
    vm: TubeViewModel,
    lib: LibraryViewModel,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenStudy: () -> Unit = {}
) {
    val context = LocalContext.current
    var topicsDialog by remember { mutableStateOf(false) }
    /** "root" is the main list; every area has its own page. */
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("root") }
    androidx.activity.compose.BackHandler(enabled = page != "root" && !vm.playerExpanded) { page = "root" }
    var clearWatch by remember { mutableStateOf(false) }
    if (clearWatch) ClearHistoryDialog(onDismiss = { clearWatch = false })
    if (topicsDialog) TopicsDialog(vm, onDismiss = { topicsDialog = false })
    var regionDialog by remember { mutableStateOf(false) }
    var qualityDialog by remember { mutableStateOf(false) }
    var appearanceDialog by remember { mutableStateOf(false) }
    var motionDialog by remember { mutableStateOf(false) }
    /** The account the ✕ was tapped on (asks before signing out of it). */
    var removeTarget by remember { mutableStateOf<com.sourabh.jarvistube.data.Account?>(null) }
    var speedDialog by remember { mutableStateOf(false) }
    var sponsorDialog by remember { mutableStateOf(false) }
    /** "font" / "size" / "color", or null when no caption dialog is open. */
    var captionDialog by remember { mutableStateOf<String?>(null) }

    // Padding on the list itself so rows don't scroll under the status bar (the header's own
    // status-bar padding is then already consumed).
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item {
            SubPageHeader(
                when (page) {
                    "account" -> "Account"; "feed" -> "Feed and topics"; "look" -> "Appearance"
                    "playback" -> "Playback"; "captions" -> "Captions"; "privacy" -> "Privacy"
                    else -> "Settings"
                },
                onBack = { if (page == "root") onBack() else page = "root" },
                large = true
            )
        }

        // ---- The main page: your account, then one row per area. Each opens its own page.
        if (page == "root") {
            item {
                val me = lib.account
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(com.sourabh.jarvistube.ui.theme.Corners.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { if (lib.signedIn) page = "account" else onSignIn() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(me?.name ?: me?.email ?: "?", 52, me?.photo)
                    Spacer(Modifier.size(14.dp))
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(me?.name ?: "Sign in", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            me?.email ?: "Your subscriptions, playlists and YouTube history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Spacer(Modifier.size(8.dp)) }
            item {
                SettingsGroup {
                    CategoryRow(Icons.Rounded.Interests, "Feed and topics",
                        if (AppSettings.userTopics.isEmpty()) "Region, topics, Shorts" else AppSettings.userTopics.take(3).joinToString(", ")) { page = "feed" }
                    CategoryRow(Icons.Rounded.Palette, "Appearance",
                        com.sourabh.jarvistube.ui.theme.EpicTheme.of(AppSettings.themeKey).title + " · " + AppSettings.appearanceName) { page = "look" }
                    CategoryRow(Icons.Rounded.PlayCircle, "Playback",
                        AppSettings.qualityLabel(AppSettings.defaultQuality) + " · SponsorBlock " + (if (Graph.player.sponsorBlock) "on" else "off")) { page = "playback" }
                    CategoryRow(Icons.Rounded.ClosedCaption, "Captions", "Look, size, colour and spoken-word highlight") { page = "captions" }
                }
            }
            item { Spacer(Modifier.size(8.dp)) }
            item {
                SettingsGroup {
                    CategoryRow(Icons.Rounded.School, "Study and focus", "Plan, focus sessions, Lock Study Session, limits", onClick = onOpenStudy)
                    CategoryRow(Icons.Rounded.Lock, "Privacy", "Watch and search history") { page = "privacy" }
                }
            }
            // ---- About, and the maker's mark: quiet, centred, at the very end.
        item {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.sourabh.jarvistube.R.drawable.osmium_badge), null,
                    Modifier.size(44.dp).clip(Corners.medium)
                )
                Spacer(Modifier.size(12.dp))
                Text("YouTube 0.1", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Ad-free, distraction-free YouTube for learning",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(8.dp))
                Text("ClearVision.Co", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("Made for focus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }

        if (page == "account") {
        // ---- Account: who you are, first.
        item {
            SettingsGroup {
                // Every Google account added to PlayBook. Tap one to switch; ✕ signs out of just that one.
                lib.accounts.forEach { a ->
                    val current = a.id == lib.account?.id
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !current) {
                                lib.switchAccount(a.id)
                                Toast.makeText(context, "Switched to ${a.name ?: a.email ?: "account"}", Toast.LENGTH_SHORT).show()
                            }
                            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(a.name ?: a.email ?: "?", 40, a.photo)
                        Spacer(Modifier.size(16.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(a.name ?: "Google account", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                a.email ?: "Loading email…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (current) {
                            Text(stringResource(R.string.using), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        androidx.compose.material3.IconButton(onClick = { removeTarget = a }) {
                            Icon(Icons.Rounded.Close, "Sign out of ${a.email ?: a.name ?: "this account"}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (lib.signedIn) {
                    SettingRow(
                        Icons.Rounded.PersonAdd, "Add another account",
                        "Switch between Google accounts any time.",
                        onClick = { lib.prepareAddAccount(); onSignIn() }
                    )
                } else {
                    SettingRow(
                        Icons.Rounded.AccountCircle, "Sign in with Google",
                        "Your subscriptions, history and playlists",
                        onClick = onSignIn
                    )
                }
            }
        }

        }

        if (page == "look") {
        // ---- Look & feel: themes from the Ramayana and the Mahabharata, each with its meaning.
        item { ThemeGallery() }
        item {
            SettingsGroup {
                SettingRow(
                    Icons.Rounded.DarkMode, "Appearance",
                    if (com.sourabh.jarvistube.ui.theme.EpicTheme.of(AppSettings.themeKey).lightOnly) "Ayodhya is always light"
                    else AppSettings.appearanceName,
                    onClick = { appearanceDialog = true }
                )
                val m = com.sourabh.jarvistube.ui.theme.MotionStyle.of(AppSettings.motionStyle)
                SettingRow(Icons.Rounded.AutoAwesome, "Animation style", "${m.title}: ${m.meaning}", onClick = { motionDialog = true })
                SettingRow(
                    Icons.Rounded.Apps, "App icon follows theme",
                    "The home-screen icon takes the theme's colours (applies when you leave the app).",
                    onClick = { AppSettings.updateIconFollowsTheme(!AppSettings.iconFollowsTheme) }
                ) { Switch(checked = AppSettings.iconFollowsTheme, onCheckedChange = { AppSettings.updateIconFollowsTheme(it) }) }
                SettingRow(
                    Icons.Rounded.Vibration, "Haptic touches", "A light tap under your finger when you choose something.",
                    onClick = { AppSettings.updateHaptics(!AppSettings.haptics) }
                ) { Switch(checked = AppSettings.haptics, onCheckedChange = { AppSettings.updateHaptics(it) }) }
                SettingRow(
                    Icons.Rounded.WbTwilight, "Ambient mode", "The video's colours glow softly behind the player.",
                    onClick = { AppSettings.updateAmbient(!AppSettings.ambient) }
                ) { Switch(checked = AppSettings.ambient, onCheckedChange = { AppSettings.updateAmbient(it) }) }
            }
        }

        }

        if (page == "playback") {
        // ---- Playback
        item {
            SettingsGroup {
                SettingRow(
                    Icons.Rounded.Tune, "Brightness and volume swipes",
                    "In full screen, swipe on the left for brightness and on the right for volume.",
                    onClick = { AppSettings.updateSwipeAdjust(!AppSettings.swipeAdjust) }
                ) { Switch(checked = AppSettings.swipeAdjust, onCheckedChange = { AppSettings.updateSwipeAdjust(it) }) }
                SettingRow(
                    Icons.Rounded.Fullscreen, "Open videos full screen",
                    "Upright, like YouTube. \"Details, comments & related\" takes you to the normal page.",
                    onClick = { AppSettings.updateStartUpright(!AppSettings.startUpright) }
                ) { Switch(checked = AppSettings.startUpright, onCheckedChange = { AppSettings.updateStartUpright(it) }) }
                SettingRow(
                    Icons.Rounded.HighQuality, "Default quality", AppSettings.qualityLabel(AppSettings.defaultQuality),
                    onClick = { qualityDialog = true }
                )
                SettingRow(
                    Icons.Rounded.Speed, "Speeds in the speed menu",
                    AppSettings.speeds.joinToString(", ") { AppSettings.speedText(it) },
                    onClick = { speedDialog = true }
                )
                SettingRow(
                    Icons.Rounded.SkipNext, "Skip sponsors (SponsorBlock)", "Jumps over sponsor reads, intros and reminders",
                    onClick = { Graph.player.updateSponsorBlock(!Graph.player.sponsorBlock) }
                ) { Switch(checked = Graph.player.sponsorBlock, onCheckedChange = { Graph.player.updateSponsorBlock(it) }) }
                if (Graph.player.sponsorBlock) SettingRow(
                    Icons.Rounded.Tune, "What to skip",
                    "${Graph.player.skipCategories.size} of ${com.sourabh.jarvistube.data.SPONSOR_CATEGORIES.size} kinds. All are marked on the seek bar.",
                    onClick = { sponsorDialog = true }
                )
            }
        }

        }

        if (page == "captions") {
        // ---- Captions: English and Hindi, one clean line.
        item {
            SettingsGroup {
                // Live preview on a video-like background; changes below show here and on the video at once.
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().padding(16.dp)
                        .clip(Corners.medium)
                        .background(androidx.compose.ui.graphics.Color(0xFF2A2F3A))
                        .heightIn(min = 96.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CaptionText(
                        "Captions will look like this",
                        fontSizeSp = 15f * AppSettings.captionScale,
                        modifier = Modifier.padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
                    )
                }
                SettingRow(
                    Icons.Rounded.FormatColorFill, "Highlight the spoken word",
                    "Lights up each word as it's said (auto-generated captions).",
                    onClick = { AppSettings.updateCaptionHighlight(!AppSettings.captionHighlight) }
                ) { Switch(checked = AppSettings.captionHighlight, onCheckedChange = { AppSettings.updateCaptionHighlight(it) }) }
                SettingRow(
                    Icons.Rounded.FontDownload, "Font",
                    AppSettings.captionFonts.firstOrNull { it.first == AppSettings.captionFont }?.second ?: "Default",
                    onClick = { captionDialog = "font" }
                )
                SettingRow(
                    Icons.Rounded.FormatSize, "Size",
                    AppSettings.captionSizes.firstOrNull { it.first == AppSettings.captionScale }?.second ?: "Normal",
                    onClick = { captionDialog = "size" }
                )
                SettingRow(
                    Icons.Rounded.Palette, "Colour",
                    AppSettings.captionColors.firstOrNull { it.first == AppSettings.captionColor }?.second ?: "White",
                    onClick = { captionDialog = "color" }
                )
            }
        }

        }

        if (page == "feed") {
        // ---- Home & tabs
        item {
            SettingsGroup {
                SettingRow(Icons.Rounded.Public, "Region", AppSettings.regionName, onClick = { regionDialog = true })
                SettingRow(
                    Icons.Rounded.Interests, "Your topics",
                    if (AppSettings.userTopics.isEmpty()) "None yet: recommendations come from your YouTube account. Tap to pick."
                    else AppSettings.userTopics.joinToString(", "),
                    onClick = { topicsDialog = true }
                )
                SettingRow(
                    Icons.Rounded.PlayCircle, "Show Shorts tab",
                    "Shorts from your topics. Off hides the tab; Shorts in search still show.",
                    onClick = { AppSettings.updateShowShorts(!AppSettings.showShorts) }
                ) { Switch(checked = AppSettings.showShorts, onCheckedChange = { AppSettings.updateShowShorts(it) }) }
            }
        }

        // ---- Focus: the Shorts block now lives here (it left the You tab).
        item { SectionHeader("Block Shorts for a while") }
        item { com.sourabh.jarvistube.ui.shorts.ShortsBlockCard() }

        }

        if (page == "privacy") {
        // ---- Privacy
        item {
            SettingsGroup {
                SettingRow(
                    Icons.Rounded.History, "Clear watch history", "${com.sourabh.jarvistube.data.WatchHistory.items.size} videos watched in this app",
                    onClick = { clearWatch = true }
                )
                SettingRow(
                    Icons.Rounded.History, "Clear search history", "${vm.recents.size} saved searches",
                    onClick = {
                        vm.clearRecents()
                        Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        }

    }

    if (regionDialog) {
        ChoiceDialog(
            title = "Region",
            options = AppSettings.regions.map { it.code to it.name },
            selected = AppSettings.region,
            onPick = { code ->
                regionDialog = false
                AppSettings.updateRegion(code)
                vm.loadStudyFeed()
            },
            onDismiss = { regionDialog = false }
        )
    }
    if (motionDialog) {
        ChoiceDialog(
            title = "Animation style",
            options = com.sourabh.jarvistube.ui.theme.MotionStyle.entries.map { it.key to "${it.title}  ·  ${it.meaning}" },
            selected = AppSettings.motionStyle,
            onPick = { key -> motionDialog = false; AppSettings.updateMotion(key) },
            onDismiss = { motionDialog = false }
        )
    }
    if (appearanceDialog) {
        ChoiceDialog(
            title = "Appearance",
            options = AppSettings.appearances,
            selected = AppSettings.appearance,
            onPick = { key ->
                appearanceDialog = false
                AppSettings.updateAppearance(key)
            },
            onDismiss = { appearanceDialog = false }
        )
    }
    if (qualityDialog) {
        ChoiceDialog(
            title = "Default quality",
            options = AppSettings.qualities.map { it to AppSettings.qualityLabel(it) },
            selected = AppSettings.defaultQuality,
            onPick = { q ->
                qualityDialog = false
                AppSettings.updateDefaultQuality(q)
            },
            onDismiss = { qualityDialog = false }
        )
    }
    when (captionDialog) {
        "font" -> ChoiceDialog(
            title = "Caption font",
            options = AppSettings.captionFonts,
            selected = AppSettings.captionFont,
            onPick = { AppSettings.updateCaptionFont(it); captionDialog = null },
            onDismiss = { captionDialog = null }
        )
        "size" -> ChoiceDialog(
            title = "Caption size",
            options = AppSettings.captionSizes,
            selected = AppSettings.captionScale,
            onPick = { AppSettings.updateCaptionScale(it); captionDialog = null },
            onDismiss = { captionDialog = null }
        )
        "color" -> ChoiceDialog(
            title = "Caption colour",
            options = AppSettings.captionColors,
            selected = AppSettings.captionColor,
            onPick = { AppSettings.updateCaptionColor(it); captionDialog = null },
            onDismiss = { captionDialog = null }
        )
    }
    if (speedDialog) SpeedListDialog(onDismiss = { speedDialog = false })
    if (sponsorDialog) SponsorCategoriesDialog(onDismiss = { sponsorDialog = false })
    removeTarget?.let { a ->
        val label = a.email ?: a.name ?: "This account"
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(R.string.sign_out_of_this_account)) },
            text = {
                Text(
                    if (lib.accounts.size > 1) "$label will be removed from PlayBook. Your other accounts stay signed in."
                    else "$label will be removed from PlayBook."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removeTarget = null
                    lib.removeAccount(a.id)
                    Toast.makeText(context, "Signed out of $label", Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.sign_out_2)) }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
internal fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Each setting's icon sits on a small tile tinted with the theme, like a phone's own settings.
        androidx.compose.foundation.layout.Box(
            Modifier.size(38.dp).clip(com.sourabh.jarvistube.ui.theme.Corners.medium)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp)) }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.size(12.dp))
            trailing()
        }
    }
}


/** One section's rows in a rounded card, like the phone's own settings. */
@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(com.sourabh.jarvistube.ui.theme.Corners.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 4.dp)
    ) { content() }
}


/** A row on the main Settings page that opens one area's page. */
@Composable
private fun CategoryRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    SettingRow(icon, title, summary, onClick = onClick) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
