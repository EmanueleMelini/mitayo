package it.emanuelemelini.mitayo.data

import it.emanuelemelini.mitayo.data.db.SkippedAnime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SkippedBackupTest {

    @Test
    fun skippedAnimeRoundTrip() {
        val skipped = listOf(SkippedAnime(21, 1_000), SkippedAnime(5114, 2_000))
        assertEquals(skipped, SkippedBackup.decode(SkippedBackup.encode(skipped)))
    }

    @Test
    fun otherFilesAreRefused() {
        assertThrows(InvalidBackupException::class.java) { SkippedBackup.decode("""{"hello":"world"}""") }
        assertThrows(InvalidBackupException::class.java) { SkippedBackup.decode("not json") }
        assertThrows(InvalidBackupException::class.java) {
            SkippedBackup.decode("""{"format":"something-else","version":1,"skipped":[]}""")
        }
    }

    @Test
    fun newerFormatsAreRefused() {
        assertThrows(InvalidBackupException::class.java) {
            SkippedBackup.decode("""{"format":"mitayo-skipped","version":99,"skipped":[]}""")
        }
    }

    @Test
    fun invalidAndRepeatedIdsAreDropped() {
        val text = """{"format":"mitayo-skipped","version":1,"skipped":[
            {"id":21,"skipped_at":1},{"id":21,"skipped_at":2},{"id":-3,"skipped_at":1},{"id":7,"skipped_at":3}]}"""
        assertEquals(listOf(SkippedAnime(21, 1), SkippedAnime(7, 3)), SkippedBackup.decode(text))
    }
}
