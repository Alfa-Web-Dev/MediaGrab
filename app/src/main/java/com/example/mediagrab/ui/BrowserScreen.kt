package com.example.mediagrab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mediagrab.BrowserController
import com.example.mediagrab.MainViewModel

@Composable
fun BrowserScreen(vm: MainViewModel, browser: BrowserController, modifier: Modifier = Modifier) {
    val url by browser.currentUrl.collectAsState()
    val progress by browser.loadProgress.collectAsState()
    val atStart by browser.atStart.collectAsState()
    val state by vm.state.collectAsState()
    val engineReady by vm.engineReady.collectAsState()
    val focus = LocalFocusManager.current

    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    // Keep the address bar in sync with navigation, unless the user is typing.
    LaunchedEffect(url, atStart) { if (!focused) text = if (atStart) "" else url }

    val canDownload = !atStart && (url.startsWith("http://") || url.startsWith("https://"))

    Column(modifier.fillMaxSize()) {
        // ---- address bar ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (!browser.back()) vm.tab.value = 0 }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Search or type a web address") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    browser.load(text)
                    focus.clearFocus()
                }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
            )
            IconButton(onClick = { browser.reload() }, enabled = !atStart) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload")
            }
            IconButton(onClick = { browser.goStart() }) {
                Icon(Icons.Default.Home, contentDescription = "Start page")
            }
        }
        if (!atStart && progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (atStart) {
                StartPage(onOpen = { browser.load(it) })
            } else {
                AndroidView(factory = { browser.webView }, modifier = Modifier.fillMaxSize())
            }

            if (canDownload) {
                ExtendedFloatingActionButton(
                    onClick = { if (engineReady && !state.loadingInfo) vm.fetch(url) },
                    icon = {
                        if (state.loadingInfo) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    },
                    text = { Text(if (state.loadingInfo) "Checking…" else "Download") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
            state.message?.takeIf { canDownload && !state.loadingInfo }?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .fillMaxWidth(0.6f),
                )
            }
        }
    }
}

@Composable
private fun StartPage(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Where to?", style = MaterialTheme.typography.titleLarge)
        Text(
            "Open a site, find a video, then tap the Download button.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(POPULAR_SITES) { site -> SiteTile(site) { onOpen(site.url) } }
        }
    }
}
