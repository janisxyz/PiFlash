package ch.leftclick.piflash

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.leftclick.piflash.ui.navigation.PiFlashNavHost
import ch.leftclick.piflash.ui.theme.PiFlashTheme
import ch.leftclick.piflash.ui.viewmodel.FlashViewModel
import ch.leftclick.piflash.ui.viewmodel.FlashViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PiFlashAppRoot()
        }
    }
}

@Composable
private fun PiFlashAppRoot() {
    PiFlashTheme(darkTheme = isSystemInDarkTheme()) {
        Surface {
            val app = LocalContext.current.applicationContext as Application
            val viewModel: FlashViewModel = viewModel(factory = FlashViewModelFactory(app))
            PiFlashNavHost(viewModel = viewModel)
        }
    }
}
