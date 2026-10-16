package com.pshenov.netwatch

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, tab: Int, large: Boolean = false) {
        val vm = NetVm(ApplicationProvider.getApplicationContext<Application>())
        vm.selectTab(tab); vm.fontLarge = large
        rule.setContent { NetWatchScreen(vm) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun home() = shot("01_home", TAB_DASH)
    @Test fun speed() = shot("02_speed", TAB_SPEED)
    @Test fun scan() = shot("03_scan", TAB_SCAN)
    @Test fun local() = shot("04_local", TAB_LOCAL)
    @Test fun diag() = shot("05_diag", TAB_DIAG)
    @Test fun wifi() = shot("06_wifi", TAB_WIFI)
    @Test fun survey() = shot("07_survey", TAB_SURVEY)
    @Test fun more() = shot("08_more", TAB_MORE)
    @Test fun monitor() = shot("09_monitor", TAB_MONITOR)
    @Test fun settings() = shot("10_settings", TAB_SETTINGS)
    @Test fun homeLarge() = shot("11_home_large", TAB_DASH, large = true)

    @Test fun updateAvailable() {
        val vm = NetVm(ApplicationProvider.getApplicationContext<Application>())
        vm.upd.info = UpdateInfo(99, "2.3", "apk/x.apk", "00", 1_100_000, "16.10.2026", listOf("Обновление прямо из приложения", "Проверка файла по SHA-256", "Мелкие исправления"))
        vm.upd.phase = UpdPhase.AVAILABLE
        vm.selectTab(TAB_SETTINGS)
        rule.setContent { NetWatchScreen(vm) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/12_update.png")
    }

    @Test fun homeBanner() {
        val vm = NetVm(ApplicationProvider.getApplicationContext<Application>())
        vm.upd.info = UpdateInfo(99, "2.3", "apk/x.apk", "00", 1_100_000, "16.10.2026", listOf("Обновление прямо из приложения"))
        vm.selectTab(TAB_DASH)
        rule.setContent { NetWatchScreen(vm) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/13_home_banner.png")
    }
}
