package io.github.rahulraj94391.keeper.data.backup

import android.content.ContentResolver
import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.io.IOException

/** Reads and writes the documents the user picked with the system file picker (SAF). */
interface BackupFiles {
    /** Writes [bytes] to the document, replacing anything there. Throws [IOException] on failure. */
    fun write(uri: String, bytes: ByteArray)

    /** Reads the document; returns null if it is larger than [maxBytes]. Throws [IOException]. */
    fun read(uri: String, maxBytes: Int): ByteArray?

    /** Best effort; used to remove a half-written export. */
    fun delete(uri: String)
}

class ContentResolverBackupFiles(context: Context) : BackupFiles {
    private val resolver: ContentResolver = context.contentResolver

    override fun write(uri: String, bytes: ByteArray) {
        // "wt" truncates, so an existing file is fully replaced rather than partly overwritten.
        val stream = resolver.openOutputStream(uri.toUri(), "wt") ?: throw IOException("Can't open document")
        stream.use { it.write(bytes) }
    }

    override fun read(uri: String, maxBytes: Int): ByteArray? {
        val stream = resolver.openInputStream(uri.toUri()) ?: throw IOException("Can't open document")
        stream.use { input ->
            // Read at most one byte past the limit, so a huge file is never loaded in full.
            val buffer = ByteArray(maxBytes + 1)
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            return if (total > maxBytes) null else buffer.copyOf(total)
        }
    }

    override fun delete(uri: String) {
        try {
            DocumentsContract.deleteDocument(resolver, uri.toUri())
        } catch (_: Exception) {
            // Best effort: some providers don't allow deleting.
        }
    }
}
