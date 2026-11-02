package com.sourabh.jarvistube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import com.sourabh.jarvistube.LibraryViewModel
import com.sourabh.jarvistube.TubeViewModel

/**
 * The "You" tab: library (history, playlists, subscription) plus Downloads and
 * Settings as sub-pages, so the bottom bar only needs Home / Study / You.
 */
@Composable
fun YouScreen(vm: TubeViewModel, lib: LibraryViewModel, bottomPadding: Dp, onSignIn: () -> Unit) {
    var route by rememberSaveable { mutableStateOf("main") }
    // Not while the full player is open: Back must collapse the player first, not close the
    // page hidden underneath it.
    BackHandler(enabled = route != "main" && !vm.playerExpanded && LocalTabActive.current) { route = "main" }

    when (route) {
        "downloads" -> SavedScreen(vm, bottomPadding, onBack = { route = "main" })
        "settings" -> SettingsScreen(vm, lib, bottomPadding, onBack = { route = "main" }, onSignIn = onSignIn)
        else -> LibraryScreen(
            vm, lib, bottomPadding, onSignIn,
            onOpenDownloads = { route = "downloads" },
            onOpenSettings = { route = "settings" }
        )
    }
}
