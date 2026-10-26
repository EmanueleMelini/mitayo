package it.emanuelemelini.mitayo.ui

import android.net.Uri
import androidx.core.net.toUri
import it.emanuelemelini.mitayo.BuildConfig

/**
 * Version of the terms of use and the privacy policy (site/terms.html, site/privacy.html),
 * accepted together before the login: raise it when either changes in substance, and the app
 * asks every user to accept them again. 2: updates from GitHub, statistics and news (1.0.0).
 */
const val POLICIES_VERSION = 2

/** Public pages: the site (site/, Cloudflare Pages) and the GitHub repository. */
object AppLinks {
    private const val REPOSITORY = "https://github.com/EmanueleMelini/mitayo"

    val site: Uri = BuildConfig.SITE_URL.toUri()
    val privacy: Uri = "${BuildConfig.SITE_URL}/privacy".toUri()
    val terms: Uri = "${BuildConfig.SITE_URL}/terms".toUri()

    /** Where users report bugs and abuse (MAL API agreement 4(c)). */
    val issues: Uri = "$REPOSITORY/issues".toUri()
    val source: Uri = REPOSITORY.toUri()
    val license: Uri = "$REPOSITORY/blob/main/LICENSE".toUri()
}
