package it.emanuelemelini.mitayo.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** Opens [uri] in a Custom Tab, or in any app that handles it. false: nothing can open it. */
fun openInBrowser(context: Context, uri: Uri): Boolean = try {
    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    true
} catch (_: ActivityNotFoundException) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

fun malAnimeUri(animeId: Int): Uri = "https://myanimelist.net/anime/$animeId".toUri()

/** A YouTube video: in the YouTube app when there is one, otherwise in the browser. */
fun openVideo(context: Context, uri: Uri): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    openInBrowser(context, uri)
}

/** Only https YouTube links: MAL's video list is shown as it comes, but nothing else gets opened. */
fun youTubeUri(url: String): Uri? {
    val uri = url.toUri()
    val host = uri.host?.removePrefix("www.")?.removePrefix("m.") ?: return null
    return uri.takeIf { it.scheme == "https" && host in setOf("youtube.com", "youtu.be") }
}
