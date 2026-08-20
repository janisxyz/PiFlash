package ch.leftclick.piflash.ui.screens

import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ch.leftclick.piflash.domain.model.ConfigPreset
import ch.leftclick.piflash.domain.model.FlashPhase
import ch.leftclick.piflash.domain.model.PiConfiguration
import ch.leftclick.piflash.domain.model.SshAuthMode
import ch.leftclick.piflash.domain.model.UsbStorageDevice
import ch.leftclick.piflash.domain.model.WifiSecurity
import ch.leftclick.piflash.domain.model.formatBytes
import ch.leftclick.piflash.domain.model.formatEta
import ch.leftclick.piflash.domain.model.formatSpeed
import ch.leftclick.piflash.ui.i18n.LocalUiText
import ch.leftclick.piflash.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    onImagePicked: (Uri) -> Unit,
    onContinue: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val t = LocalUiText.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImagePicked)
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(t.appName) },
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = t.settings)
                }
            }
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(t.homeIntro)
            Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.SdCard, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(t.chooseImage)
            }
            state.image?.let { img ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(img.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("${formatBytes(img.sizeBytes)} · ${img.compression}")
                    }
                }
            }
            OutlinedButton(onClick = onOpenTemplates, modifier = Modifier.fillMaxWidth()) {
                Text(t.editTemplates)
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onContinue,
                enabled = state.image != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text(t.continueLabel) }
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
    val t = LocalUiText.current
    fun isSelected(dev: UsbStorageDevice): Boolean {
        val sel = state.selectedDevice ?: return false
        return sel.vendorId == dev.vendorId &&
            sel.productId == dev.productId &&
            sel.device.deviceName == dev.device.deviceName
    }

    val canContinue = state.selectedDevice != null &&
        state.acknowledgedErase &&
        (state.selectedDevice?.hasPermission == true)

    Scaffold(topBar = {
        TopAppBar(title = { Text(t.sdCard) }, navigationIcon = {
            TextButton(onClick = onBack) { Text(t.back) }
        })
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.devices.isEmpty()) {
                Text(t.plugInReader)
            }
            state.devices.forEach { dev ->
                val selected = isSelected(dev)
                Card(
                    onClick = { onSelect(dev) },
                    modifier = Modifier.fillMaxWidth(),
                    border = if (selected) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else null,
                    colors = if (selected) {
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    } else {
                        CardDefaults.cardColors()
                    }
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (selected) Icons.Filled.CheckCircle else Icons.Filled.Memory,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(dev.name, style = MaterialTheme.typography.titleMedium)
                            val perm = when {
                                selected && dev.hasPermission -> t.selectedPermissionGranted
                                selected && !dev.hasPermission -> t.selectedWaitingPermission
                                dev.hasPermission -> t.permissionGrantedTap
                                else -> t.tapToSelectAllow
                            }
                            Text("VID ${dev.vendorId.toString(16)} PID ${dev.productId.toString(16)} · $perm")
                        }
                    }
                }
            }

            if (state.selectedDevice != null) {
                Text(
                    "${t.selectedPrefix} ${state.selectedDevice!!.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "  ${t.eraseWarning}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.acknowledgedErase,
                            onCheckedChange = onAcknowledge
                        )
                        Text(t.eraseAcknowledge)
                    }
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (state.selectedDevice != null && state.selectedDevice?.hasPermission != true) {
                Text(
                    t.usbPermissionHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onContinue,
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        state.selectedDevice == null -> t.selectADevice
                        !state.acknowledgedErase -> t.confirmEraseToContinue
                        state.selectedDevice?.hasPermission != true -> t.waitingUsbPermission
                        else -> t.continueLabel
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    state: UiState,
    onChange: ((PiConfiguration) -> PiConfiguration) -> Unit,
    onApplyPreset: (ConfigPreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onBack: () -> Unit,
    onFlash: () -> Unit
) {
    val t = LocalUiText.current
    val c = state.config
    fun set(block: PiConfiguration.() -> PiConfiguration) = onChange { it.block() }
    val credentialsOk = c.username.isNotBlank() && (c.password.isNotBlank() || c.sshPublicKey.isNotBlank())
    val hasImage = state.image != null
    val hasDevice = state.selectedDevice != null && state.selectedDevice?.hasPermission == true
    val canFlash = credentialsOk && hasImage && hasDevice && state.acknowledgedErase
    var showSave by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ConfigPreset?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(t.headlessSetup) }, navigationIcon = {
            TextButton(onClick = onBack) { Text(t.back) }
        })
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!hasDevice) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        t.noCardBanner,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(t.templates, style = MaterialTheme.typography.titleSmall)
            Text(
                t.templatesHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.templates.forEach { preset ->
                    FilterChip(
                        selected = state.activePresetId == preset.id,
                        onClick = { onApplyPreset(preset) },
                        label = { Text(t.presetLabel(preset)) }
                    )
                }
            }

            Text(t.yourPresets, style = MaterialTheme.typography.titleSmall)
            if (state.presets.isEmpty()) {
                Text(
                    t.noPresetsYet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.presets.forEach { preset ->
                        InputChip(
                            selected = state.activePresetId == preset.id,
                            onClick = { onApplyPreset(preset) },
                            label = { Text(preset.name) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { pendingDelete = preset },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "${t.delete} ${preset.name}",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    saveName = state.presets.find { it.id == state.activePresetId }?.name
                        ?: c.hostname.ifBlank { "Preset" }
                    showSave = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.activePresetId != null && state.presets.any { it.id == state.activePresetId }) {
                        t.updateOrSavePreset
                    } else {
                        t.saveAsPreset
                    }
                )
            }

            OutlinedTextField(c.hostname, { v -> set { copy(hostname = v) } }, label = { Text(t.hostname) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(c.username, { v -> set { copy(username = v) } }, label = { Text(t.username) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                c.password,
                { v -> set { copy(password = v) } },
                label = { Text(t.password) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Text(t.ssh)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = c.enableSsh, onClick = { set { copy(enableSsh = !enableSsh) } }, label = { Text(t.enableSsh) })
                FilterChip(selected = c.sshAuthMode == SshAuthMode.PASSWORD, onClick = { set { copy(sshAuthMode = SshAuthMode.PASSWORD) } }, label = { Text(t.sshPassword) })
                FilterChip(selected = c.sshAuthMode == SshAuthMode.KEY, onClick = { set { copy(sshAuthMode = SshAuthMode.KEY) } }, label = { Text(t.sshKey) })
                FilterChip(selected = c.sshAuthMode == SshAuthMode.BOTH, onClick = { set { copy(sshAuthMode = SshAuthMode.BOTH) } }, label = { Text(t.sshBoth) })
            }
            OutlinedTextField(c.sshPublicKey, { v -> set { copy(sshPublicKey = v) } }, label = { Text(t.sshPublicKey) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Text(t.wifi)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = c.enableWifi, onCheckedChange = { v -> set { copy(enableWifi = v) } })
                Text(t.configureWifi)
            }
            OutlinedTextField(c.wifiSsid, { v -> set { copy(wifiSsid = v) } }, label = { Text(t.ssid) }, modifier = Modifier.fillMaxWidth(), enabled = c.enableWifi)
            OutlinedTextField(
                c.wifiPassword,
                { v -> set { copy(wifiPassword = v) } },
                label = { Text(t.wifiPassword) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                enabled = c.enableWifi
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = c.wifiSecurity == WifiSecurity.WPA2, onClick = { set { copy(wifiSecurity = WifiSecurity.WPA2) } }, label = { Text(t.wpa2) }, enabled = c.enableWifi)
                FilterChip(selected = c.wifiSecurity == WifiSecurity.WPA3, onClick = { set { copy(wifiSecurity = WifiSecurity.WPA3) } }, label = { Text(t.wpa3) }, enabled = c.enableWifi)
                FilterChip(selected = c.wifiSecurity == WifiSecurity.OPEN, onClick = { set { copy(wifiSecurity = WifiSecurity.OPEN) } }, label = { Text(t.openWifi) }, enabled = c.enableWifi)
            }
            OutlinedTextField(c.country, { v -> set { copy(country = v.uppercase()) } }, label = { Text(t.country) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(c.timezone, { v -> set { copy(timezone = v) } }, label = { Text(t.timezone) }, modifier = Modifier.fillMaxWidth())
            Text(t.firstBoot)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = c.aptUpdateUpgrade, onCheckedChange = { v -> set { copy(aptUpdateUpgrade = v) } })
                Text(t.aptUpdateUpgrade)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = c.installCoolify, onCheckedChange = { v -> set { copy(installCoolify = v) } })
                Text(t.installCoolify)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onFlash, enabled = canFlash, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        !credentialsOk -> t.flashNeedsCredentials
                        !hasImage && !hasDevice -> t.flashNeedsImageAndCard
                        !hasImage -> t.flashNeedsImage
                        !hasDevice -> t.flashNeedsCard
                        !state.acknowledgedErase -> t.flashNeedsAck
                        else -> t.flashSdCard
                    }
                )
            }
        }
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text(t.savePresetTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        t.savePresetHint,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        label = { Text(t.name) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSavePreset(saveName)
                        showSave = false
                    },
                    enabled = saveName.isNotBlank()
                ) { Text(t.save) }
            },
            dismissButton = {
                TextButton(onClick = { showSave = false }) { Text(t.cancel) }
            }
        )
    }

    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(t.deletePresetTitle.format(preset.name)) },
            text = { Text(t.deletePresetBody) },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePreset(preset.id)
                    pendingDelete = null
                }) { Text(t.delete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(t.cancel) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashProgressScreen(
    state: UiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    val t = LocalUiText.current
    val p = state.progress
    val view = LocalView.current

    DisposableEffect(p.phase) {
        val window = (view.context as? android.app.Activity)?.window
        val busy = p.phase != FlashPhase.SUCCESS &&
            p.phase != FlashPhase.FAILED &&
            p.phase != FlashPhase.CANCELLED &&
            p.phase != FlashPhase.IDLE
        if (busy) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(p.phase) {
        if (p.phase == FlashPhase.SUCCESS) onDone()
    }

    val percent = if (p.totalBytes > 0) {
        ((p.bytesWritten * 100) / p.totalBytes).toInt().coerceIn(0, 100)
    } else null

    Scaffold(topBar = { TopAppBar(title = { Text(t.flashing) }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                t.phaseTitle(p.phase),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                p.message.ifBlank {
                    when (p.phase) {
                        FlashPhase.PREPARING -> t.msgPreparing
                        FlashPhase.WRITING -> t.msgWriting
                        FlashPhase.SYNCING -> t.msgSyncing
                        FlashPhase.CONFIGURING -> t.msgConfiguring
                        else -> ""
                    }
                },
                style = MaterialTheme.typography.bodyLarge
            )

            if (p.totalBytes > 0 && p.phase == FlashPhase.WRITING) {
                LinearProgressIndicator(
                    progress = { p.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "$percent%  ·  ${formatBytes(p.bytesWritten)} / ${formatBytes(p.totalBytes)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${formatSpeed(p.bytesPerSecond)}  ·  ETA ${formatEta(p.totalBytes - p.bytesWritten, p.bytesPerSecond)}"
                )
            } else if (
                p.phase == FlashPhase.PREPARING ||
                p.phase == FlashPhase.WRITING ||
                p.phase == FlashPhase.SYNCING ||
                p.phase == FlashPhase.CONFIGURING ||
                p.phase == FlashPhase.VERIFYING
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (p.bytesWritten > 0) {
                    Text("${formatBytes(p.bytesWritten)} written")
                }
            } else if (p.phase == FlashPhase.SUCCESS) {
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            p.error?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.weight(1f))

            when (p.phase) {
                FlashPhase.FAILED, FlashPhase.CANCELLED ->
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Text(t.back)
                    }
                FlashPhase.SUCCESS ->
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text(t.done)
                    }
                else ->
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(t.cancel)
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuccessScreen(state: UiState, onHome: () -> Unit) {
    val t = LocalUiText.current
    val s = state.progress.summary
    Scaffold(topBar = { TopAppBar(title = { Text(t.readyTitle) }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(t.cardFlashed, style = MaterialTheme.typography.headlineSmall)
            Text("${t.hostnameLabel} ${s?.hostname ?: state.config.hostname}")
            Text(s?.imageName ?: state.image?.displayName ?: "")
            Text(t.insertCard)
            Spacer(Modifier.weight(1f))
            Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text(t.flashAnother) }
        }
    }
}
