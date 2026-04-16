package dev.uclip.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HistoryScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Clipboard history")
        Text("History will appear here once sync is active.")
    }
}
