package it.emanuelemelini.mitayo.data.auth

import java.security.SecureRandom

/**
 * PKCE values for the MAL login. MAL supports only the "plain" method, so the code
 * challenge sent to the authorize page is the verifier itself.
 */
object Pkce {
    /** RFC 7636 "unreserved" characters. */
    private const val CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    /** MAL accepts 43 to 128 characters: the longest is the hardest to guess. */
    const val VERIFIER_LENGTH = 128

    private val random = SecureRandom()

    fun newVerifier(): String = randomString(VERIFIER_LENGTH)

    /** Ties the redirect to the login this app started. */
    fun newState(): String = randomString(32)

    private fun randomString(length: Int): String =
        buildString(length) { repeat(length) { append(CHARSET[random.nextInt(CHARSET.length)]) } }
}
