package it.emanuelemelini.mitayo.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun tagsAndShortVersionsParse() {
        assertEquals(AppVersion.parse("0.2.0"), AppVersion.parse("v0.2.0"))
        assertEquals(AppVersion.parse("1.2"), AppVersion.parse("1.2.0"))
        assertEquals("0.2.0", AppVersion.parse("v0.2.0-beta+1").toString())
    }

    @Test
    fun versionsCompareByNumbers() {
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.9")!!)
        assertTrue(AppVersion.parse("1.0.0")!! > AppVersion.parse("0.2.0")!!)
    }

    @Test
    fun textThatIsntAVersionIsRejected() {
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1.x.0"))
    }
}
