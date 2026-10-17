package com.l1ttlehe11.netwatch

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

    @Test fun moreWithHistory() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        History.init(ctx)
        val now = System.currentTimeMillis()
        History.add(HItem(1, now - 3_600_000, HKind.DNS, "Wi-Fi", "лучший: Провайдер / роутер · 11 мс", "", ""))
        History.add(HItem(2, now - 1_800_000, HKind.MONITOR, "Wi-Fi", "Мониторинг 31м 01с · прервана", "обрывов 0 · без интернета 0с · потери 0.0%", ""))
        History.add(HItem(3, now - 60_000, HKind.SCAN, "Wi-Fi", "3 устройств", "", ""))
        val vm = NetVm(ctx)
        vm.selectTab(TAB_MORE)
        rule.setContent { NetWatchScreen(vm) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/14_more_history.png")
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
