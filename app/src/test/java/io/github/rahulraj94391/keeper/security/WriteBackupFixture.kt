package io.github.rahulraj94391.keeper.security

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Regenerates the frozen v1 backup fixture. Skipped while `src/test/resources/backup/v1-fixture.kbak`
 * exists. To regenerate deliberately: delete that file, run the unit tests, then copy the file
 * this writes under `app/build/` back into resources. Only do that for an intended format change.
 */
class WriteBackupFixture {
    @Test
    fun writeWhenMissing() {
        assumeTrue(javaClass.getResource("/backup/v1-fixture.kbak") == null)
        val out = File("build/generated-fixtures/v1-fixture.kbak").apply { parentFile?.mkdirs() }
        out.writeBytes(
            BackupCryptoTest.fixtureWriter()
                .encrypt(BackupCryptoTest.fixtureCredentials(), BackupCryptoTest.FIXTURE_PASSPHRASE.toCharArray())
        )
        error("Fixture was missing; wrote ${out.absolutePath}. Copy it to src/test/resources/backup/.")
    }
}
