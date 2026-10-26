package it.emanuelemelini.mitayo

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Languages the app is translated into; the first one is the default (values/). */
enum class AppLanguage(val tag: String) {
    ITALIAN("it"),
    ENGLISH("en"),
}

/** The language chosen in the app (Profile → Appearance), or the device's. */
object AppLocale {

    /** null when the app follows the device language. */
    fun chosen(): AppLanguage? {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return null
        return AppLanguage.entries.find { it.tag == locales[0]?.language }
    }

    /** null = follow the device language. The activity is recreated in the new language. */
    fun choose(language: AppLanguage?) {
        AppCompatDelegate.setApplicationLocales(
            language?.let { LocaleListCompat.forLanguageTags(it.tag) } ?: LocaleListCompat.getEmptyLocaleList()
        )
    }

    /**
     * A context whose resources are in the app language, for text made outside an activity
     * (notifications, the news topic). From Android 13 the system applies it to the whole app.
     */
    fun localizedContext(context: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || locales.isEmpty) return context
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locales[0]) }
        return context.createConfigurationContext(configuration)
    }
}
