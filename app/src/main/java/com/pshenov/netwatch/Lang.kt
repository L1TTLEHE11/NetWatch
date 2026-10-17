package com.pshenov.netwatch

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Язык интерфейса: «как в системе» или выбранный вручную.
 * Android 13+: выбор хранит система (он же виден в «Настройки → Приложения → NetWatch → Язык»).
 * Android 8–12: выбор хранится в настройках приложения и применяется через attachBaseContext.
 */
object Lang {
    /** Поддерживаемые языки. Английский — язык по умолчанию (values/strings.xml). */
    val supported = listOf("en", "ru")
    private const val PREFS = "lang"

    @Volatile private var res: Resources? = null
    @Volatile var locale: Locale = Locale.getDefault(); private set

    /** Выбранный тег языка или "" — как в системе. */
    fun chosen(ctx: Context): String =
        if (Build.VERSION.SDK_INT >= 33) ctx.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language ?: ""
        else ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tag", "") ?: ""

    /** Язык, который реально показывается: выбранный, иначе системный, если он поддерживается, иначе английский. */
    fun effective(ctx: Context): Locale {
        val tag = chosen(ctx)
        if (tag.isNotEmpty()) return Locale.forLanguageTag(tag)
        val sys = if (Build.VERSION.SDK_INT >= 24) Resources.getSystem().configuration.locales[0] else @Suppress("DEPRECATION") Resources.getSystem().configuration.locale
        return if (sys.language in supported) sys else Locale.ENGLISH
    }

    /** Контекст с нужным языком — для Activity, сервиса, виджета. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base          // система применяет язык сама
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tag", "") ?: ""
        if (tag.isEmpty()) return base
        val conf = Configuration(base.resources.configuration)
        conf.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return base.createConfigurationContext(conf)
    }

    /** Обновить ресурсы для tr() — при запуске и после смены языка. */
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext ?: ctx
        val loc = effective(app)
        val conf = Configuration(app.resources.configuration).apply { setLocales(LocaleList(loc)) }
        res = app.createConfigurationContext(conf).resources
        locale = loc
    }

    /** Сменить язык: "" — как в системе, "en", "ru". */
    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            refresh(activity)                                   // система пересоздаст экран сама
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tag", tag).commit()
            refresh(activity)
            activity.recreate()
        }
    }

    /** Для тестов: зафиксировать язык независимо от системы и настроек. */
    fun pin(ctx: Context, tag: String) {
        val app = ctx.applicationContext ?: ctx
        val loc = Locale.forLanguageTag(tag)
        val conf = Configuration(app.resources.configuration).apply { setLocales(LocaleList(loc)) }
        res = app.createConfigurationContext(conf).resources; locale = loc
    }

    internal fun resources(): Resources = res ?: throw IllegalStateException("Lang.refresh() не вызван")
}

/** Перевод строки на текущий язык интерфейса. */
fun tr(@StringRes id: Int, vararg args: Any?): String =
    if (args.isEmpty()) Lang.resources().getString(id) else Lang.resources().getString(id, *args)

class NetWatchApp : Application() {
    override fun attachBaseContext(base: Context) { super.attachBaseContext(Lang.wrap(base)) }
    override fun onCreate() { super.onCreate(); Lang.refresh(this) }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); Lang.refresh(this) }
}
