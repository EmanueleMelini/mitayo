package it.emanuelemelini.mitayo.data.update

import android.content.Context
import android.content.pm.PackageManager
import it.emanuelemelini.mitayo.BuildConfig
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Notes of one version, by language tag ("en", "it"). */
data class ChangelogEntry(val version: AppVersion, val notes: Map<String, List<String>>) {
    /** Notes in the app language, in English when that language is missing. */
    fun notesFor(languageTag: String): List<String> = notes[languageTag] ?: notes[Release.ENGLISH].orEmpty()
}

/**
 * The notes of every version, read from assets/release-notes/X.Y.Z.md: the same files the release
 * workflow publishes on GitHub, so the app shows them offline too.
 */
class Changelog(private val context: Context) {

    /** Newest first. */
    suspend fun entries(): List<ChangelogEntry> = withContext(Dispatchers.IO) {
        context.assets.list(DIRECTORY).orEmpty()
            .mapNotNull { name ->
                val version = AppVersion.parse(name.removeSuffix(".md")) ?: return@mapNotNull null
                val text = context.assets.open("$DIRECTORY/$name").bufferedReader().use { it.readText() }
                ChangelogEntry(version, GitHubReleasesClient.parseNotes(text))
            }
            .sortedByDescending { it.version }
    }

    /** Versions after [after] (null: from the first) up to [upTo], newest first. */
    suspend fun between(after: AppVersion?, upTo: AppVersion): List<ChangelogEntry> =
        entries().filter { (after == null || it.version > after) && it.version <= upTo }

    private companion object {
        const val DIRECTORY = "release-notes"
    }
}

/** Shows the changelog once, at the first start after an update. */
class WhatsNew(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val changelog: Changelog,
) {

    /** Notes still to show; empty after a fresh install or when already seen. */
    suspend fun pending(): List<ChangelogEntry> {
        val current = AppVersion.parse(BuildConfig.VERSION_NAME) ?: return emptyList()
        val lastSeen = settingsRepository.lastSeenVersion()?.let(AppVersion::parse)
        val notes = when {
            // Nothing new for someone who has just installed the app
            lastSeen == null && isFreshInstall() -> emptyList()
            // Update from 0.1.0, which didn't save the version seen: only the current notes
            lastSeen == null -> changelog.between(null, current).take(1)
            else -> changelog.between(lastSeen, current)
        }
        // With nothing to show there is no dialog to close, so it's seen now
        if (notes.isEmpty() && lastSeen != current) markSeen()
        return notes
    }

    suspend fun markSeen() {
        settingsRepository.setLastSeenVersion(BuildConfig.VERSION_NAME)
    }

    private fun isFreshInstall(): Boolean = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    } catch (_: PackageManager.NameNotFoundException) {
        true
    }
}
