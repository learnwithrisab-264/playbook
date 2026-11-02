package com.sourabh.jarvistube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.LibTab
import com.sourabh.jarvistube.LibraryViewModel
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.ChannelRef
import com.sourabh.jarvistube.data.ChannelRepo
import com.sourabh.jarvistube.data.LibChannel
import com.sourabh.jarvistube.data.StudyChannels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page as NpPage

@Composable
internal fun StudyChannelsScreen(vm: TubeViewModel, lib: LibraryViewModel, bottomPadding: Dp) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChannelRef>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lib.signedIn) { if (lib.signedIn) lib.ensure(LibTab.Channels) }
    val subs = if (lib.signedIn) {
        lib.loader(LibTab.Channels).state.items.filterIsInstance<LibChannel>()
            .map { ChannelRef("https://www.youtube.com/channel/${it.id}", it.name, it.thumb) }
    } else emptyList()

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        searching = true
        searchError = null
        scope.launch {
            try {
                results = ChannelRepo.searchChannels(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchError = e.message ?: e.javaClass.simpleName
            } finally {
                searching = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item { PageTopBar("My Channels", vm::popPage) }
        item {
            Text(
                "Home shows new videos only from these channels, and they're always allowed in Study mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search channels, e.g. JEE Wallah") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        if (searching) item { Spinner() }
        searchError?.let { e -> item { ErrorBox(e, onRetry = { search() }) } }
        if (results.isNotEmpty()) {
            item { SectionTitle("Search results") }
            items(results, key = { "r:" + it.url }) { c -> ChannelPickRow(vm, c) }
        }
        if (StudyChannels.list.isNotEmpty()) {
            item { SectionTitle("Your channels") }
            items(StudyChannels.list.toList(), key = { "s:" + it.url }) { c -> ChannelPickRow(vm, c) }
        }
        if (subs.isNotEmpty()) {
            item { SectionTitle("From your subscriptions") }
            items(subs, key = { "y:" + it.url }) { c -> ChannelPickRow(vm, c) }
        }
    }
}

@Composable
internal fun ChannelPickRow(vm: TubeViewModel, c: ChannelRef) {
    val added = StudyChannels.contains(c.url)
    Row(
        Modifier.fillMaxWidth().clickable { vm.openChannel(c.url, c.name) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(c.name, 44, c.avatar)
        Spacer(Modifier.size(14.dp))
        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (added) {
            FilledTonalButton(onClick = { StudyChannels.toggle(c) }) { Text("Added") }
        } else {
            OutlinedButton(onClick = { StudyChannels.toggle(c) }) { Text("Add") }
        }
    }
}

// ------------------------------------------------------------------ shared bits
