package com.example.mediagrab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mediagrab.MainViewModel
import com.example.mediagrab.MediaInfo
import com.example.mediagrab.Preset
import com.example.mediagrab.UiState

/** Quality picker with an estimated size next to every option. Shown from Home and the browser. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(vm: MainViewModel, info: MediaInfo, state: UiState) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = state.selectedPreset
    val needed = (selected?.sizeBytes ?: 0L) * 2   // file is staged in cache, then copied to Downloads
    val lowSpace = needed > 0 && vm.freeSpaceBytes() < needed

    ModalBottomSheet(onDismissRequest = vm::dismissInfo, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp, 68.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(info.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    info.uploader?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    if (info.durationSec > 0) {
                        Text(formatDuration(info.durationSec), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Video", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                info.presets.filter { !it.audioOnly }.forEach { PresetRow(it, selected?.id == it.id) { vm.onPresetSelected(it) } }
                Text(
                    "Audio only",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                info.presets.filter { it.audioOnly }.forEach { PresetRow(it, selected?.id == it.id) { vm.onPresetSelected(it) } }
            }

            Text(
                "Sizes are estimates and can differ slightly after download.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lowSpace) {
                Text(
                    "Low storage: you need about ${formatSize(needed).removePrefix("~")} free while this downloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val sizeText = selected?.sizeBytes?.let { formatSize(it) }.orEmpty()
            Button(
                onClick = {
                    vm.addToQueue()
                    vm.tab.value = 2
                },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (sizeText.isEmpty()) "Download" else "Download  ·  $sizeText") }
        }
    }
}

@Composable
private fun PresetRow(preset: Preset, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(preset.label, Modifier.padding(start = 12.dp).weight(1f))
        Text(
            formatSize(preset.sizeBytes).ifEmpty { "size unknown" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
