package io.github.rahulraj94391.keeper.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One login, fully encrypted (PRD 6.4, 8). Title, username, password, URL and notes exist only
 * inside [ciphertext]; nothing searchable or readable is stored in plain columns.
 */
@Entity(tableName = "credentials")
class CredentialEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "payload_schema_version") val payloadSchemaVersion: Int,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)
