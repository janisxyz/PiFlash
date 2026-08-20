package ch.leftclick.piflash.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.leftclick.piflash.domain.model.FlashPhase
import ch.leftclick.piflash.domain.model.PiConfiguration
import ch.leftclick.piflash.domain.model.SshAuthMode
import ch.leftclick.piflash.domain.model.UsbStorageDevice
import ch.leftclick.piflash.domain.model.WifiSecurity
import ch.leftclick.piflash.domain.model.formatBytes
import ch.leftclick.piflash.domain.model.formatEta
import ch.leftclick.piflash.domain.model.formatSpeed
import ch.leftclick.piflash.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    onImagePicked: (Uri) -> Unit,
    onContinue: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImagePicked)
    }
    Scaffold(topBar = { TopAppBar(title = { Text("PiFlash") }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Flash Raspberry Pi OS from your phone. Select a .img / .img.xz / .img.gz file.")
            Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.SdCard, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Choose image")
            }
            state.image?.let { img ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(img.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("${formatBytes(img.sizeBytes)} · ${img.compression}")
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onContinue,
                enabled = state.image != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Continue") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    state: UiState,
    onSelect: (UsbStorageDevice) -> Unit,
    onAcknowledge: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("SD card") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.devices.isEmpty()) {
                Text("Plug in a USB-C SD reader. Mass-storage devices will appear here.")
            }
            state.devices.forEach { dev ->
                Card(onClick = { onSelect(dev) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Memory, contentDescription = null)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(dev.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "VID ${dev.vendorId.toString(16)} PID ${dev.productId.toString(16)}" +
                                    if (dev.hasPermission) " · permission granted" else " · tap to allow"
                            )
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("  This will ERASE the entire card.", color = MaterialTheme.colorScheme.error)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.acknowledgedErase, onCheckedChange = onAcknowledge)
                        Text("I understand all data will be destroyed")
                    }
                }
            }
            Button(
                onClick = onContinue,
                enabled = state.selectedDevice != null && state.acknowledgedErase &&
                    (state.selectedDevice?.hasPermission == true),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Continue") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    state: UiState,
    onChange: ((PiConfiguration) -> PiConfiguration) -> Unit,
    onBack: () -> Unit,
    onFlash: () -> Unit
) {
    val c = state.config
    Scaffold(topBar = { TopAppBar(title = { Text("Headless setup") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(c.hostname, { onChange { it.copy(hostname = it2(it, it.hostname, it2 = null); } }, label = { Text("Hostname") }, modifier = Modifier.fillMaxWidth())
        }
    }
}
