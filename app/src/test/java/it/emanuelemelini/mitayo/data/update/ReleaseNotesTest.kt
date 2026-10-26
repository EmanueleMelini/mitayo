package it.emanuelemelini.mitayo.data.update

import it.emanuelemelini.mitayo.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseNotesTest {

    @Test
    fun englishIsVisibleAndItalianIsInTheHiddenBlock() {
        val notes = GitHubReleasesClient.parseNotes(
            "- One\n- Two\n\n<!-- mitayo-notes:it\n- Uno\n- Due\n-->\n"
        )
        assertEquals(listOf("One", "Two"), notes["en"])
        assertEquals(listOf("Uno", "Due"), notes["it"])
    }

    /** The release workflow and the in-app "What's new" both read this file. */
    @Test
    fun currentVersionHasNotesInBothLanguages() {
        val file = File("src/main/assets/release-notes/${BuildConfig.VERSION_NAME}.md")
        assertTrue("Missing ${file.path}", file.exists())
        val notes = GitHubReleasesClient.parseNotes(file.readText())
        assertTrue("No English notes", notes["en"].orEmpty().isNotEmpty())
        assertTrue("No Italian notes", notes["it"].orEmpty().isNotEmpty())
    }
}
