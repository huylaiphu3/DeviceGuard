package com.deviceguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deviceguard.ui.navigation.DeviceGuardNavigation
import com.deviceguard.ui.screen.OnboardingScreen
import com.deviceguard.ui.theme.DeviceGuardTheme
import com.deviceguard.ui.viewmodel.DeviceGuardViewModels
import com.deviceguard.ui.viewmodel.SettingsViewModel

/**
 * Điểm vào duy nhất của ứng dụng.
 *
 * Cổng chặn quan trọng: chừng nào [SettingsViewModel.termsAccepted] còn false thì
 * chỉ hiện màn hình Onboarding — không collector nào chạy trước thời điểm đó.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DeviceGuardTheme {
                val settingsViewModel = viewModel<SettingsViewModel>(
                    factory = DeviceGuardViewModels.Factory
                )
                val accepted by settingsViewModel.termsAccepted.collectAsState()

                if (accepted) {
                    DeviceGuardNavigation()
                } else {
                    OnboardingScreen(onAccepted = { settingsViewModel.acceptTerms() })
                }
            }
        }
    }
}
