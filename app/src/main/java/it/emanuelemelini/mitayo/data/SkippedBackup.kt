package it.emanuelemelini.mitayo.data

import android.content.Context
import android.net.Uri
import it.emanuelemelini.mitayo.data.db.SkippedAnime
import it.emanuelemelini.mitayo.data.db.SkippedDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/** Not a Mitayo file of skipped anime, or a newer format than this version reads. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The skipped anime in a JSON file the user picks (Storage Access Framework, no permission):
 * they live only on this phone, and go with a logout or an uninstall. Only ids and times: no
 * titles or other MAL data.
 */
class SkippedBackup(private val context: Context, private val skippedDao: SkippedDao) {

    /** Returns how many anime went into the file. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val skipped = skippedDao.all()
        val text = encode(skipped)
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't write $uri")
        stream.use { it.write(text.toByteArray()) }
        skipped.size
    }

    /** Adds the file's anime to the skipped ones (the ones already there keep their time); returns how many were new. */
    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't read $uri")
        val text = stream.use { input ->
            val bytes = input.readNBytesCompat(MAX_FILE_BYTES + 1)
            if (bytes.size > MAX_FILE_BYTES) throw InvalidBackupException("File too big")
            bytes.decodeToString()
        }
        val skipped = decode(text)
        val existing = skippedDao.all().mapTo(HashSet()) { it.animeId }
        val added = skipped.filter { it.animeId !in existing }
        skippedDao.insertAll(added)
        added.size
    }

    companion object {
        private const val FORMAT = "mitayo-skipped"
        private const val VERSION = 1
        /** About a million ids: far more than anyone skips. */
        private const val MAX_FILE_BYTES = 32 * 1024 * 1024

        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        internal fun encode(skipped: List<SkippedAnime>): String =
            json.encodeToString(BackupFile.serializer(), BackupFile(FORMAT, VERSION, skipped.map { BackupItem(it.animeId, it.skippedAt) }))

        internal fun decode(text: String): List<SkippedAnime> {
            val file = try {
                json.decodeFromString(BackupFile.serializer(), text)
            } catch (e: SerializationException) {
                throw InvalidBackupException("Not a skipped anime file", e)
            } catch (e: IllegalArgumentException) {
                throw InvalidBackupException("Not a skipped anime file", e)
            }
            if (file.format != FORMAT) throw InvalidBackupException("Not a skipped anime file")
            if (file.version > VERSION) throw InvalidBackupException("Newer format ${file.version}")
            return file.skipped.filter { it.id > 0 && it.skippedAt >= 0 }.distinctBy { it.id }.map { SkippedAnime(it.id, it.skippedAt) }
        }

        private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (out.size() < limit) {
                val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    @Serializable
    private data class BackupFile(
        val format: String,
        val version: Int,
        val skipped: List<BackupItem>,
    )

    @Serializable
    private data class BackupItem(
        val id: Int,
        @SerialName("skipped_at") val skippedAt: Long,
    )
}
