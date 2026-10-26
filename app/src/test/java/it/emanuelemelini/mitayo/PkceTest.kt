package it.emanuelemelini.mitayo

import it.emanuelemelini.mitayo.data.auth.Pkce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {

    @Test
    fun verifierHasTheLengthAndCharactersMalAccepts() {
        val verifier = Pkce.newVerifier()
        assertEquals(Pkce.VERIFIER_LENGTH, verifier.length)
        assertTrue(verifier.length in 43..128)
        assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" })
    }

    @Test
    fun everyLoginGetsNewValues() {
        assertNotEquals(Pkce.newVerifier(), Pkce.newVerifier())
        assertNotEquals(Pkce.newState(), Pkce.newState())
    }
}
