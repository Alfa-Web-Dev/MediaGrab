package com.example.mediagrab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import java.util.Locale

/** "~85 MB", "~1.20 GB" or "" when unknown. */
fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1024L * 1024L -> "~${bytes / 1024} KB"
    bytes < 1024L * 1024L * 1024L -> "~${bytes / (1024 * 1024)} MB"
    else -> "~" + String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

fun formatDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

data class Site(val name: String, val domain: String, val url: String)

val POPULAR_SITES = listOf(
    Site("YouTube", "youtube.com", "https://m.youtube.com"),
    Site("Vimeo", "vimeo.com", "https://vimeo.com"),
    Site("Dailymotion", "dailymotion.com", "https://www.dailymotion.com"),
    Site("SoundCloud", "soundcloud.com", "https://soundcloud.com"),
    Site("Facebook", "facebook.com", "https://m.facebook.com"),
    Site("Instagram", "instagram.com", "https://www.instagram.com"),
    Site("TikTok", "tiktok.com", "https://www.tiktok.com"),
    Site("X", "x.com", "https://x.com"),
)

/** Round site shortcut. The icon is fetched at runtime; a letter badge shows if it fails. */
@Composable
fun SiteTile(site: Site, onClick: () -> Unit) {
    Column(
        Modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            SubcomposeAsyncImage(
                model = "https://www.google.com/s2/favicons?domain=${site.domain}&sz=128",
                contentDescription = site.name,
                modifier = Modifier.size(30.dp),
                loading = { LetterBadge(site.name) },
                error = { LetterBadge(site.name) },
            )
        }
        Text(
            site.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LetterBadge(name: String) {
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Text(name.take(1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}
