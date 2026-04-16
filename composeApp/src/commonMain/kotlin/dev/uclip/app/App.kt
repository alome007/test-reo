package dev.uclip.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.uclip.app.pairing.PairingHost
import dev.uclip.app.ui.DevicesScreen
import dev.uclip.app.ui.HistoryScreen
import dev.uclip.app.ui.SettingsScreen

enum class Tab(val label: String) { Devices("Devices"), History("History"), Settings("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme {
        var tab by remember { mutableStateOf(Tab.Devices) }
        Scaffold(
            topBar = { TopAppBar(title = { Text("Universal Clipboard") }) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {},
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.Devices -> DevicesScreen()
                    Tab.History -> HistoryScreen()
                    Tab.Settings -> SettingsScreen()
                }
            }
            PairingHost()
        }
    }
}
