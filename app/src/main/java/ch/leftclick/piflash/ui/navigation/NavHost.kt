package ch.leftclick.piflash.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ch.leftclick.piflash.domain.model.FlashPhase
import ch.leftclick.piflash.ui.screens.ConfigScreen
import ch.leftclick.piflash.ui.screens.DeviceScreen
import ch.leftclick.piflash.ui.screens.FlashProgressScreen
import ch.leftclick.piflash.ui.screens.HomeScreen
import ch.leftclick.piflash.ui.screens.SuccessScreen
import ch.leftclick.piflash.ui.viewmodel.FlashViewModel

object Routes {
    const val HOME = "home"
    const val DEVICE = "device"
    const val CONFIG = "config"
    const val PROGRESS = "progress"
    const val SUCCESS = "success"
}

@Composable
fun PiFlashNavHost(viewModel: FlashViewModel) {
    val nav = rememberNavController()
    val state by viewModel.state.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                state = state,
                onImagePicked = viewModel::onImagePicked,
                onContinue = { nav.navigate(Routes.DEVICE) }
            )
        }
        composable(Routes.DEVICE) {
            DeviceScreen(
                state = state,
                onSelect = viewModel::selectDevice,
                onAcknowledge = viewModel::acknowledgeErase,
                onBack = { nav.popBackStack() },
                onContinue = { nav.navigate(Routes.CONFIG) }
            )
        }
        composable(Routes.CONFIG) {
            ConfigScreen(
                state = state,
                onChange = viewModel::updateConfig,
                onApplyPreset = viewModel::applyPreset,
                onSavePreset = viewModel::savePreset,
                onDeletePreset = viewModel::deletePreset,
                onBack = { nav.popBackStack() },
                onFlash = {
                    viewModel.startFlash()
                    nav.navigate(Routes.PROGRESS)
                }
            )
        }
        composable(Routes.PROGRESS) {
            FlashProgressScreen(
                state = state,
                onCancel = viewModel::cancelFlash,
                onRetry = {
                    viewModel.reset()
                    nav.popBackStack(Routes.CONFIG, inclusive = false)
                },
                onDone = {
                    if (state.progress.phase == FlashPhase.SUCCESS) {
                        nav.navigate(Routes.SUCCESS) { popUpTo(Routes.HOME) { inclusive = false } }
                    }
                }
            )
        }
        composable(Routes.SUCCESS) {
            SuccessScreen(
                state = state,
                onHome = {
                    viewModel.reset()
                    nav.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }
    }
}
