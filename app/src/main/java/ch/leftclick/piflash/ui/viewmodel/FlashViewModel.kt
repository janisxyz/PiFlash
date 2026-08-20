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
import ch.leftclick.piflash.domain.model.FlashPhase
import ch.leftclick.piflash.domain.model.FlashProgress
import ch.leftclick.piflash.domain.model.PiConfiguration
import ch.leftclick.piflash.domain.model.SelectedImage
import ch.leftclick.piflash.domain.model.UsbStorageDevice
import ch.leftclick.piflash.domain.usb.UsbStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val image: SelectedImage? = null,
    val devices: List<UsbStorageDevice> = emptyList(),
    val selectedDevice: UsbStorageDevice? = null,
    val config: PiConfiguration = PiConfiguration(),
    val progress: FlashProgress = FlashProgress(FlashPhase.IDLE),
    val error: String? = null,
    val acknowledgedErase: Boolean = false
)

class FlashViewModel(app: Application) : AndroidViewModel(app) {
    private val usb = UsbStorageManager(app)
    private val analyzer = ImageAnalyzer(app)
    private val session = FlashSession(usb, ImageDecompressor(app))

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var flashJob: Job? = null

    init {
        viewModelScope.launch {
            usb.observeDevices().collect { list ->
                _state.update { it.copy(devices = list) }
            }
        }
    }

    fun onImagePicked(uri: Uri) {
        runCatching { analyzer.analyze(uri) }
            .onSuccess { img -> _state.update { it.copy(image = img, error = null) } }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }

    fun selectDevice(device: UsbStorageDevice) {
        if (!device.hasPermission) {
            usb.requestPermission(device.device)
        }
        _state.update { it.copy(selectedDevice = device, acknowledgedErase = false) }
    }

    fun acknowledgeErase(value: Boolean) {
        _state.update { it.copy(acknowledgedErase = value) }
    }

    fun updateConfig(transform: (PiConfiguration) -> PiConfiguration) {
        _state.update { it.copy(config = transform(it.config)) }
    }

    fun startFlash() {
        val s = _state.value
        val image = s.image ?: return
        val device = s.selectedDevice ?: return
        if (!s.acknowledgedErase) {
            _state.update { it.copy(error = "Confirm that the SD card will be erased") }
            return
        }
        // Show progress UI immediately so the screen never looks frozen
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
        // Heavy USB I/O + decompression MUST run off the main thread
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
                            error = ch.leftclick.piflash.domain.model.FlashError(
                                t.message ?: t.javaClass.simpleName,
                                t
                            )
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
