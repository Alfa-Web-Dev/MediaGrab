package com.example.mediagrab.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mediagrab.DownloadCenter
import com.example.mediagrab.DownloadItem
import com.example.mediagrab.R
import com.example.mediagrab.Status

@Composable
fun DownloadsScreen(modifier: Modifier = Modifier) {
    val items by DownloadCenter.items.collectAsState()
    val context = LocalContext.current
    val sorted = items.sortedByDescending { it.createdAt }
    val active = items.count { it.status == Status.RUNNING || it.status == Status.QUEUED }

    Column(modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Downloads", style = MaterialTheme.typography.headlineMedium)
                if (active > 0) {
                    Text("$active in progress", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(
                onClick = { DownloadCenter.clearFinished() },
                enabled = items.any { it.status != Status.QUEUED && it.status != Status.RUNNING },
            ) { Text("Clear finished") }
        }

        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(painterResource(R.drawable.ic_empty), contentDescription = null, modifier = Modifier.size(160.dp, 120.dp))
                    Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Paste a link on Home or open a site in the browser.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(sorted, key = { it.id }) { item -> DownloadRow(item, context) }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, context: Context) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(statusLine(item), style = MaterialTheme.typography.bodySmall)
                if (item.status == Status.RUNNING) {
                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                }
                item.error?.takeIf { item.status == Status.FAILED }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Column {
                when (item.status) {
                    Status.QUEUED, Status.RUNNING -> IconButton(onClick = { DownloadCenter.cancel(item.id) }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                    Status.DONE -> {
                        IconButton(onClick = { openFile(context, item) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Open")
                        }
                        IconButton(onClick = { DownloadCenter.remove(item.id, deleteFile = true) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete file")
                        }
                    }
                    Status.FAILED, Status.CANCELLED -> {
                        IconButton(onClick = { DownloadCenter.retry(context, item.id) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                        }
                        IconButton(onClick = { DownloadCenter.remove(item.id, deleteFile = false) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }
}

private fun statusLine(item: DownloadItem): String {
    val size = formatSize(item.sizeBytes)
    val base = listOf(item.presetLabel, size).filter { it.isNotEmpty() }.joinToString(" · ")
    return when (item.status) {
        Status.QUEUED -> "$base · Waiting"
        Status.RUNNING -> "$base · ${(item.progress * 100).toInt()}%  ${item.eta}".trim()
        Status.DONE -> "$base · Saved"
        Status.FAILED -> "$base · Failed"
        Status.CANCELLED -> "$base · Cancelled"
    }
}

private fun openFile(context: Context, item: DownloadItem) {
    val uri = item.savedUri?.let(Uri::parse) ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No app can open this file type; ignore.
    }
}
