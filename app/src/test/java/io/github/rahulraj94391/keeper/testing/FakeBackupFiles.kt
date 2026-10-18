package io.github.rahulraj94391.keeper.testing

import io.github.rahulraj94391.keeper.data.backup.BackupFiles
import java.io.IOException

/** In-memory stand-in for documents picked through the system file picker. */
class FakeBackupFiles : BackupFiles {
    val documents = mutableMapOf<String, ByteArray>()
    var failWrites = false

    override fun write(uri: String, bytes: ByteArray) {
        if (failWrites) {
            documents[uri] = bytes.copyOf(bytes.size / 2) // a partial write before the failure
            throw IOException("disk full")
        }
        documents[uri] = bytes
    }

    override fun read(uri: String, maxBytes: Int): ByteArray? {
        val bytes = documents[uri] ?: throw IOException("no such document")
        return if (bytes.size > maxBytes) null else bytes
    }

    override fun delete(uri: String) {
        documents.remove(uri)
    }
}
