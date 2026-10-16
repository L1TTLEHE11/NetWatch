package com.pshenov.netwatch

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private val vm: NetVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // экран не гаснет, пока открыт мониторинг
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val ctl = WindowCompat.getInsetsController(window, window.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.isAppearanceLightStatusBars = false
        // сразу полноэкранный режим: статус-бар и навигация скрыты, вызываются свайпом от края
        ctl.hide(WindowInsetsCompat.Type.systemBars())
        setContent { NetWatchScreen(vm) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // после диалогов/шторки снова прячем панели
        if (hasFocus) WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    // в фоне все замеры замирают — не жрём батарею и трафик
    override fun onStart() { super.onStart(); vm.onAppVisible(true); vm.refreshReport() }
    override fun onStop() { super.onStop(); vm.onAppVisible(false) }
}
