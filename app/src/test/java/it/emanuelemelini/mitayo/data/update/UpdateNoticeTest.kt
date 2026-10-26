package it.emanuelemelini.mitayo.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNoticeTest {

    private fun v(text: String) = AppVersion.parse(text)

    @Test
    fun newerReleaseIsAnnouncedOnce() {
        assertTrue(UpdateNotice.shouldNotify(v("0.2.0"), v("0.3.0"), lastNotified = null))
        assertFalse(UpdateNotice.shouldNotify(v("0.2.0"), v("0.3.0"), lastNotified = v("0.3.0")))
        assertTrue(UpdateNotice.shouldNotify(v("0.2.0"), v("0.4.0"), lastNotified = v("0.3.0")))
    }

    @Test
    fun sameOrOlderReleaseIsNotAnnounced() {
        assertFalse(UpdateNotice.shouldNotify(v("0.3.0"), v("0.3.0"), lastNotified = null))
        assertFalse(UpdateNotice.shouldNotify(v("0.3.0"), v("0.2.0"), lastNotified = null))
        assertFalse(UpdateNotice.shouldNotify(v("0.3.0"), null, lastNotified = null))
    }
}
