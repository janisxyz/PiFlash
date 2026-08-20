package ch.leftclick.piflash.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ch.leftclick.piflash.domain.flash.FlashSession
import ch.leftclick.piflash.domain.image.ImageAnalyzer
import ch.leftclick.piflash.domain.image.ImageDecompressor
import ch.leftclick.piflash.domain.model.ConfigPreset
import ch.leftclick.piflash.domain.model.ConfigTemplates
import ch.leftclick.piflash.domain.model.FlashError
import ch.leftclick.piflash.domain.model.FlashPhase
import ch.leftclick.piflash.domain.model.FlashProgress
import ch.leftclick.piflash.domain.model.PiConfiguration
import ch.leftclick.piflash.domain.model.SelectedImage
import ch.leftclick.piflash.domain.model.UsbStorageDevice
import ch.leftclick.piflash.domain.preset.PresetStore
import ch.leftclick.piflash.domain.usb.UsbStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val image: SelectedImage? = null,
    val devices: List<UsbStorageDevice> = emptyList(),
    val selectedDevice: UsbStorageDevice? = null,
    val config: PiConfiguration = PiConfiguration(),
    val templates: List<ConfigPreset> = ConfigTemplates.all,
    val presets: List<ConfigPreset> = emptyList(),
    val activePresetId: String? = null,
    val progress: FlashProgress = FlashProgress(FlashPhase.IDLE),
    val error: String? = null,
    val acknowledgedErase: Boolean = false
)

class FlashViewModel(app: Application) : AndroidViewModel(app) {
    private val usb = UsbStorageManager(app)
    private val analyzer = ImageAnalyzer(app)
    private val session = FlashSession(usb, ImageDecompressor(app))
    private val presetStore = PresetStore(app.filesDir)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var flashJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val (saved, last) = presetStore.load()
            _state.update {
                it.copy(
                    presets = saved,
                    config = last ?: it.config
                )
            }
        }
        viewModelScope.launch {
            usb.observeDevices().collect { list ->
                _state.update { prev ->
                    // Refresh selected device from the new list (permission may have changed)
                    val refreshed = prev.selectedDevice?.let { sel ->
                        list.find { sameDevice(it, sel) }
                    }
                    // Auto-select when there is exactly one device and nothing is selected yet
                    val auto = when {
                        refreshed != null -> refreshed
                        prev.selectedDevice == null && list.size == 1 -> list.first()
                        prev.selectedDevice != null && list.none { sameDevice(it, prev.selectedDevice!!) } -> null
                        else -> prev.selectedDevice
                    }
                    // Request permission for auto-selected device if needed
                    if (auto != null && !auto.hasPermission && prev.selectedDevice == null && list.size == 1) {
                        usb.requestPermission(auto.device)
                    }
                    prev.copy(
                        devices = list,
                        selectedDevice = auto
                    )
                }
            }
        }
    }

    private fun sameDevice(a: UsbStorageDevice, b: UsbStorageDevice): Boolean =
        a.vendorId == b.vendorId && a.productId == b.productId && a.device.deviceName == b.device.deviceName

    fun onImagePicked(uri: Uri) {
        runCatching { analyzer.analyze(uri) }
            .onSuccess { img -> _state.update { it.copy(image = img, error = null) } }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }

    fun selectDevice(device: UsbStorageDevice) {
        if (!device.hasPermission) {
            usb.requestPermission(device.device)
        }
        _state.update { prev ->
            val switching = prev.selectedDevice != null && !sameDevice(prev.selectedDevice!!, device)
            prev.copy(
                selectedDevice = device,
                // Only clear the erase acknowledgement when switching to a different device
                acknowledgedErase = if (switching) false else prev.acknowledgedErase,
                error = null
            )
        }
    }

    fun acknowledgeErase(value: Boolean) {
        _state.update { prev ->
            // If user ticks the box and there is exactly one device, select it automatically
            val selected = prev.selectedDevice
                ?: prev.devices.singleOrNull()?.also { dev ->
                    if (!dev.hasPermission) usb.requestPermission(dev.device)
                }
            prev.copy(
                acknowledgedErase = value,
                selectedDevice = selected ?: prev.selectedDevice
            )
        }
    }

    fun updateConfig(transform: (PiConfiguration) -> PiConfiguration) {
        _state.update { it.copy(config = transform(it.config), activePresetId = null) }
    }

    fun applyPreset(preset: ConfigPreset) {
        val current = _state.value.config
        val incoming = preset.config
        val next = if (preset.builtIn) {
            // Templates fill hostname / first-boot flags; keep secrets already typed in the form.
            incoming.copy(
                password = incoming.password.ifBlank { current.password },
                sshPublicKey = incoming.sshPublicKey.ifBlank { current.sshPublicKey },
                wifiSsid = if (incoming.enableWifi) incoming.wifiSsid.ifBlank { current.wifiSsid } else incoming.wifiSsid,
                wifiPassword = if (incoming.enableWifi) incoming.wifiPassword.ifBlank { current.wifiPassword } else incoming.wifiPassword
            )
        } else {
            incoming
        }
        _state.update { it.copy(config = next, activePresetId = preset.id, error = null) }
    }

    fun savePreset(name: String) {
        val trimmed = name.trim().ifBlank { _state.value.config.hostname.ifBlank { "Preset" } }
        val current = _state.value.config
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _state.value.presets.find { it.name.equals(trimmed, ignoreCase = true) }
            val preset = ConfigPreset(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = trimmed,
                config = current,
                savedAt = System.currentTimeMillis(),
                builtIn = false
            )
            val next = (_state.value.presets.filter { it.id != preset.id } + preset)
                .sortedBy { it.name.lowercase() }
            presetStore.save(next, current)
            _state.update { it.copy(presets = next, activePresetId = preset.id) }
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val next = _state.value.presets.filter { it.id != id }
            presetStore.save(next, _state.value.config)
            _state.update {
                it.copy(
                    presets = next,
                    activePresetId = if (it.activePresetId == id) null else it.activePresetId
                )
            }
        }
    }

    fun startFlash() {
        val s = _state.value
        val image = s.image ?: return
        val device = s.selectedDevice ?: return
        if (!s.acknowledgedErase) {
            _state.update { it.copy(error = "Confirm that the SD card will be erased") }
            return
        }
        if (!device.hasPermission) {
            usb.requestPermission(device.device)
            _state.update { it.copy(error = "Grant USB permission, then try again") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            presetStore.save(s.presets, s.config)
        }
        _state.update {
            it.copy(
                progress = FlashProgress(
                    phase = FlashPhase.PREPARING,
                    message = "Starting…"
                ),
                error = null
            )
        }
        flashJob?.cancel()
        flashJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                session.run(image, device.device, s.config).collect { p ->
                    _state.update { it.copy(progress = p, error = p.error?.message) }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.update {
                    it.copy(
                        progress = FlashProgress(
                            phase = FlashPhase.FAILED,
                            message = t.message ?: "Flash failed",
                            error = FlashError(t.message ?: t.javaClass.simpleName, t)
                        ),
                        error = t.message
                    )
                }
            }
        }
    }

    fun cancelFlash() {
        flashJob?.cancel()
        _state.update {
            it.copy(
                progress = FlashProgress(
                    phase = FlashPhase.CANCELLED,
                    message = "Cancelled"
                )
            )
        }
    }

    fun reset() {
        flashJob?.cancel()
        _state.update {
            it.copy(
                progress = FlashProgress(FlashPhase.IDLE),
                error = null,
                acknowledgedErase = false
            )
        }
    }
}

class FlashViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FlashViewModel(app) as T
    }
}
