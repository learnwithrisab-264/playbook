package com.sourabh.jarvistube.ui.search

import com.sourabh.jarvistube.ui.common.feedItems
import com.sourabh.jarvistube.ui.common.LoadMoreEffect
import com.sourabh.jarvistube.ui.common.TopicChip
import com.sourabh.jarvistube.ui.shorts.rememberShortsBlockLeft
import com.sourabh.jarvistube.ui.shorts.ShortsGrid
import com.sourabh.jarvistube.ui.shorts.ShortsLocked
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.TubeViewModel

/** Full-screen search like YouTube's: back arrow + field, then suggestions, then results. */
@Composable
fun SearchOverlay(vm: TubeViewModel, bottomPadding: Dp = 0.dp) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val shortsGrid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val submitted = vm.submittedQuery
    val showResults = submitted != null && vm.query.trim() == submitted

    LaunchedEffect(Unit) { requester.requestFocus() }

    fun submit(text: String) {
        focus.clearFocus()
        keyboard?.hide()
        vm.submitSearch(text)
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeSearch) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            TextField(
                value = vm.query,
                onValueChange = { vm.query = it; vm.updateSuggestions(it) },
                singleLine = true,
                placeholder = { Text("Search PlayBook") },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.clearSearch(); vm.suggestions.clear(); requester.requestFocus() }) {
                            Icon(Icons.Rounded.Close, "Clear")
                        }
                    }
                },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(vm.query) }),
                modifier = Modifier.weight(1f).focusRequester(requester)
            )
        }

        if (showResults) {
            // Two sections, never mixed. No Shorts section during a study session or lock.
            val shortsOpen = vm.searchShowsShorts && vm.shortsAllowed
            if (vm.shortsAllowed) Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopicChip("Videos", !shortsOpen) { vm.searchShowsShorts = false }
                TopicChip("Shorts", shortsOpen) { vm.searchShowsShorts = true }
            }
            if (shortsOpen) {
                val blockedFor = rememberShortsBlockLeft()
                if (blockedFor > 0) ShortsLocked(blockedFor)
                else ShortsGrid(
                    vm.shortsResults, shortsGrid, bottomPadding,
                    onRetry = { vm.submitSearch(submitted!!) },
                    onPlay = { v -> vm.openShorts({ vm.shortsResults.items }, v, vm::loadMoreSearchShorts) },
                    onMore = vm::loadMoreSearchShorts,
                    emptyBody = "No Shorts matched this search.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 24.dp)) {
                feedItems(vm.results, onRetry = { vm.submitSearch(submitted!!) }, onPlay = vm::play)
            }
            LoadMoreEffect(listState, vm.results.items.size, vm::loadMoreSearch)
            }
        } else {
            val rows: List<Pair<String, Boolean>> = if (vm.query.isBlank()) {
                vm.recents.map { it to true }
            } else {
                vm.suggestions.map { it to false }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)) {
                items(rows, key = { (text, recent) -> "$recent:$text" }) { (text, recent) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { submit(text) }.padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (recent) Icons.Rounded.History else Icons.Rounded.Search, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(20.dp))
                        Text(text, Modifier.weight(1f).padding(vertical = 14.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        IconButton(onClick = { vm.query = text; vm.updateSuggestions(text) }) {
                            Icon(Icons.Rounded.NorthWest, "Use as search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (recent) {
                            IconButton(onClick = { vm.removeRecent(text) }) {
                                Icon(Icons.Rounded.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
