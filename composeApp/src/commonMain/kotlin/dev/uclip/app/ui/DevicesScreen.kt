package dev.uclip.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DevicesScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Paired devices")
        Text("No devices yet. Pair your first device from the menu bar / Quick Settings tile.")
        Button(onClick = {}) { Text("Pair new device") }
    }
}
