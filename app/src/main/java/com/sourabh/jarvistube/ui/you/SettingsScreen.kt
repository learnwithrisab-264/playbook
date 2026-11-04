package com.sourabh.jarvistube.ui.you

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
    onSignIn: () -> Unit
) {
    val context = LocalContext.current
    var regionDialog by remember { mutableStateOf(false) }
    var qualityDialog by remember { mutableStateOf(false) }
    var appearanceDialog by remember { mutableStateOf(false) }
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
        item { SubPageHeader("Settings", onBack) }

        item { SectionHeader("Appearance") }
        item {
            SettingRow(
                Icons.Rounded.DarkMode, "Appearance", AppSettings.appearanceName,
                onClick = { appearanceDialog = true }
            )
        }

        item { SectionHeader("Content") }
        item {
            SettingRow(
                Icons.Rounded.Public, "Region", AppSettings.regionName,
                onClick = { regionDialog = true }
            )
        }
        item {
            SettingRow(
                Icons.Rounded.Explore, "Show Recommended tab",
                "Your YouTube home feed, or Trending when signed out. Hidden during study sessions and locks.",
                onClick = { AppSettings.updateShowRecommended(!AppSettings.showRecommended) }
            ) {
                Switch(
                    checked = AppSettings.showRecommended,
                    onCheckedChange = { AppSettings.updateShowRecommended(it) }
                )
            }
        }
        item {
            SettingRow(
                Icons.Rounded.PlayCircle, "Show Shorts tab",
                "The Shorts tab in the bottom bar. Shorts in search results still show.",
                onClick = { AppSettings.updateShowShorts(!AppSettings.showShorts) }
            ) {
                Switch(
                    checked = AppSettings.showShorts,
                    onCheckedChange = { AppSettings.updateShowShorts(it) }
                )
            }
        }

        item { SectionHeader("Playback") }
        item {
            SettingRow(
                Icons.Rounded.HighQuality, "Default quality", AppSettings.qualityLabel(AppSettings.defaultQuality),
                onClick = { qualityDialog = true }
            )
        }
        item {
            SettingRow(
                Icons.Rounded.SkipNext, "Skip sponsors (SponsorBlock)",
                "Jumps over sponsor reads, intros and reminders",
                onClick = { Graph.player.updateSponsorBlock(!Graph.player.sponsorBlock) }
            ) {
                Switch(
                    checked = Graph.player.sponsorBlock,
                    onCheckedChange = { Graph.player.updateSponsorBlock(it) }
                )
            }
        }
        if (Graph.player.sponsorBlock) item {
            SettingRow(
                Icons.Rounded.SkipNext, "What to skip",
                "${Graph.player.skipCategories.size} of ${com.sourabh.jarvistube.data.SPONSOR_CATEGORIES.size} kinds. All are marked on the seek bar.",
                onClick = { sponsorDialog = true }
            )
        }

        item { SectionHeader("Playback speed") }
        item {
            SettingRow(
                Icons.Rounded.Speed, "Speeds in the speed menu",
                AppSettings.speeds.joinToString(", ") { AppSettings.speedText(it) },
                onClick = { speedDialog = true }
            )
        }

        item { SectionHeader("Captions") }
        item {
            // Live preview on a video-like background; changes below show here and on the video at once.
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
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
        }
        item {
            SettingRow(
                Icons.Rounded.FontDownload, "Font",
                AppSettings.captionFonts.firstOrNull { it.first == AppSettings.captionFont }?.second ?: "Default",
                onClick = { captionDialog = "font" }
            )
        }
        item {
            SettingRow(
                Icons.Rounded.FormatSize, "Size",
                AppSettings.captionSizes.firstOrNull { it.first == AppSettings.captionScale }?.second ?: "Normal",
                onClick = { captionDialog = "size" }
            )
        }
        item {
            SettingRow(
                Icons.Rounded.Palette, "Colour",
                AppSettings.captionColors.firstOrNull { it.first == AppSettings.captionColor }?.second ?: "White",
                onClick = { captionDialog = "color" }
            )
        }

        item { SectionHeader("Privacy") }
        item {
            SettingRow(
                Icons.Rounded.History, "Clear search history", "${vm.recents.size} saved searches",
                onClick = {
                    vm.clearRecents()
                    Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item { SectionHeader("Account") }
        // Every Google account added to PlayBook (photo, name, email). Tap one to switch to it;
        // ✕ signs out of / removes just that account.
        items(lib.accounts, key = { it.id }) { a ->
            val current = a.id == lib.account?.id
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = !current) {
                        lib.switchAccount(a.id)
                        Toast.makeText(context, "Switched to ${a.name ?: a.email ?: "account"}", Toast.LENGTH_SHORT).show()
                    }
                    .padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
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
        item {
            if (lib.signedIn) {
                SettingRow(
                    Icons.Rounded.PersonAdd, "Add another account",
                    "Sign in with a different Google account. You can switch any time.",
                    onClick = { lib.prepareAddAccount(); onSignIn() }
                )
            } else {
                SettingRow(
                    Icons.Rounded.AccountCircle, "Sign in with Google",
                    "See your subscriptions, history and playlists",
                    onClick = onSignIn
                )
            }
        }

        item { SectionHeader("About") }
        item {
            SettingRow(
                Icons.Rounded.Info, "PlayBook 0.1",
                "Ad-free, distraction-free YouTube for learning",
                onClick = {}
            )
        }
        // Maker's mark: quiet, centred, at the very end of Settings.
        item {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.sourabh.jarvistube.R.drawable.osmium_badge), null,
                    Modifier.size(44.dp).clip(Corners.medium)
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    "Designed & built by Sourabh",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Made for focus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.size(12.dp))
            trailing()
        }
    }
}
