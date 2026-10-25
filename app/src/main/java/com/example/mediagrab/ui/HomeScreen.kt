package com.example.mediagrab.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mediagrab.DownloadCenter
import com.example.mediagrab.MainViewModel
import com.example.mediagrab.R
import com.example.mediagrab.Status

@Composable
fun HomeScreen(vm: MainViewModel, onOpenSite: (String) -> Unit, modifier: Modifier = Modifier) {
    val s by vm.state.collectAsState()
    val engineReady by vm.engineReady.collectAsState()
    val engineError by vm.engineError.collectAsState()
    val items by DownloadCenter.items.collectAsState()
    val clipboard = LocalClipboardManager.current
    val recent = items.sortedByDescending { it.createdAt }.take(3)

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---- hero ----
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(HeroBrush)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("MediaGrab", style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White)
                Text(
                    "Paste a link or browse a site, pick a quality, and save it to your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                )
            }
            Image(painterResource(R.drawable.ic_hero), contentDescription = null, modifier = Modifier.size(104.dp))
        }

        when {
            engineError != null -> Text(engineError!!, color = MaterialTheme.colorScheme.error)
            !engineReady -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  Preparing engine (first launch only)…", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- link box ----
        OutlinedTextField(
            value = s.url,
            onValueChange = vm::onUrlChange,
            label = { Text("Paste a link") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                TextButton(onClick = {
                    clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.onUrlChange(it) }
                }) { Text("Paste") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.fetch() },
            enabled = engineReady && !s.loadingInfo && s.url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (s.loadingInfo) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Text("  Checking link…")
            } else Text("Get info")
        }
        s.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        // ---- popular sites ----
        Text("Browse a site", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(POPULAR_SITES) { site -> SiteTile(site) { onOpenSite(site.url) } }
        }

        // ---- recent ----
        if (recent.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { vm.tab.value = 2 }) { Text("See all") }
            }
            recent.forEach { item ->
                Card(Modifier.fillMaxWidth().clickable { vm.tab.value = 2 }) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = item.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp, 42.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when (item.status) {
                                    Status.DONE -> "Saved"
                                    Status.RUNNING -> "${(item.progress * 100).toInt()}%"
                                    Status.QUEUED -> "Waiting"
                                    Status.FAILED -> "Failed"
                                    Status.CANCELLED -> "Cancelled"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        Text(
            "Only download content you own or have permission to save.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
