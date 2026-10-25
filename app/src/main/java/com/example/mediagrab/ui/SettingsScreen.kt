package com.example.mediagrab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mediagrab.MainViewModel
import com.example.mediagrab.Settings

private val QUALITIES = listOf(
    "best" to "Best available",
    "1080" to "1080p",
    "720" to "720p",
    "480" to "480p",
    "360" to "360p",
    "mp3" to "MP3 (audio)",
    "m4a" to "M4A (audio)",
)

private val THEMES = listOf(0 to "System default", 1 to "Light", 2 to "Dark")

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val quality by Settings.defaultQuality.collectAsState()
    val theme by Settings.theme.collectAsState()
    val dynamic by Settings.dynamicColors.collectAsState()
    val embed by Settings.embedMetadata.collectAsState()
    val wifi by Settings.wifiOnly.collectAsState()
    val folder by Settings.folder.collectAsState()
    val concurrent by Settings.concurrent.collectAsState()
    val fast by Settings.fastMode.collectAsState()
    val compat by Settings.compatible.collectAsState()
    val s by vm.state.collectAsState()
    val engineReady by vm.engineReady.collectAsState()

    var folderText by remember(folder) { mutableStateOf(folder) }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Section("Speed")
        Text("Simultaneous downloads: $concurrent", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = concurrent.toFloat(),
            onValueChange = { Settings.setConcurrent(it.toInt()) },
            valueRange = 1f..5f,
            steps = 3,
        )
        ToggleRow("Fast mode (parallel fragments)", fast) { Settings.setFastMode(it) }
        Text(
            "Speeds up streaming-style sites. Actual speed still depends on the website and your network.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Section("Default quality")
        QUALITIES.forEach { (id, label) ->
            Choice(label, selected = quality == id) { Settings.setDefaultQuality(id) }
        }
        ToggleRow("Prefer compatible files (MP4 / M4A)", compat) { Settings.setCompatible(it) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Section("Appearance")
        THEMES.forEach { (id, label) ->
            Choice(label, selected = theme == id) { Settings.setTheme(id) }
        }
        ToggleRow("Use system colours (Android 12+)", dynamic) { Settings.setDynamicColors(it) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Section("Downloads")
        OutlinedTextField(
            value = folderText,
            onValueChange = { folderText = it; Settings.setFolder(it) },
            label = { Text("Folder inside Downloads") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ToggleRow("Embed metadata and cover art", embed) { Settings.setEmbedMetadata(it) }
        ToggleRow("Download on Wi-Fi only", wifi) { Settings.setWifiOnly(it) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Section("Engine")
        Text(
            "If downloads start failing, update the engine. Websites change often, so this fixes most errors.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = vm::updateEngine,
            enabled = engineReady && !s.updatingEngine,
        ) { Text(if (s.updatingEngine) "Updating…" else "Update engine") }
        s.engineMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Section("About")
        Text(
            "MediaGrab runs entirely on your device. It is powered by yt-dlp (Unlicense), " +
                "youtubedl-android (GPL-3.0) and FFmpeg. Download only content you own or have " +
                "permission to save, and respect each website's terms.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text("  $label")
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
