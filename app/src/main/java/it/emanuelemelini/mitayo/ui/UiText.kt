package it.emanuelemelini.mitayo.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import it.emanuelemelini.mitayo.AppContainer
import it.emanuelemelini.mitayo.MitayoApp

/**
 * Text created outside the UI (ViewModels, errors) and resolved in the current language
 * only when shown, so a language change doesn't leave stale strings behind.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Res -> stringResource(id, *args.map { (it as? UiText)?.asString() ?: it }.toTypedArray())
        is Plural -> pluralStringResource(id, count, *args.map { (it as? UiText)?.asString() ?: it }.toTypedArray())
    }

    /** Outside composition, e.g. for a snackbar. Nested UiText arguments are resolved too. */
    fun resolve(context: Context): String = when (this) {
        is Res -> context.getString(id, *args.map { (it as? UiText)?.resolve(context) ?: it }.toTypedArray())
        is Plural -> context.resources.getQuantityString(id, count, *args.map { (it as? UiText)?.resolve(context) ?: it }.toTypedArray())
    }
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/** Access to the dependency container to create ViewModels (manual DI). */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as MitayoApp).container
