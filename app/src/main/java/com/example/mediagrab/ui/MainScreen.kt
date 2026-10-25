package com.example.mediagrab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.mediagrab.BrowserController
import com.example.mediagrab.MainViewModel
import com.example.mediagrab.R

@Composable
fun MainScreen(vm: MainViewModel) {
    val tab by vm.tab.collectAsState()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Lives as long as the screen, so the browser page is kept when you switch tabs.
    val browser = remember { BrowserController(context) }

    BackHandler(enabled = tab != 0) {
        if (tab == 1) {
            if (!browser.back()) vm.tab.value = 0
        } else {
            vm.tab.value = 0
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { vm.tab.value = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { vm.tab.value = 1 },
                    icon = { Icon(painterResource(R.drawable.ic_globe), contentDescription = null) },
                    label = { Text("Browser") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { vm.tab.value = 2 },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("Downloads") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { vm.tab.value = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            0 -> HomeScreen(vm, onOpenSite = { url -> browser.load(url); vm.tab.value = 1 }, modifier = m)
            1 -> BrowserScreen(vm, browser, m)
            2 -> DownloadsScreen(m)
            else -> SettingsScreen(vm, m)
        }
    }

    // Quality + size picker, shared by Home and the browser.
    state.info?.let { DownloadSheet(vm, it, state) }
}
