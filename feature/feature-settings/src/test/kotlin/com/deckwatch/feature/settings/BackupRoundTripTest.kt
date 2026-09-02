package com.deckwatch.feature.settings

import com.deckwatch.feature.settings.backup.BackupArchive
import com.deckwatch.feature.settings.backup.BackupCrypto
import com.deckwatch.feature.settings.backup.BackupPhoto
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The `.dwbackup` container of §18, both ways: plain zip and passphrase-protected.
 *
 * Pure JVM — `BackupArchive` and `BackupCrypto` touch no Android API, which is exactly why the zip
 * and the crypto live apart from `BackupManager`.
 */
class BackupRoundTripTest {

    private val payload = """{"schemaVersion":1,"vessels":[{"id":"v1","name":"MV Example"}]}"""
    private val photoBytes = mapOf(
        "content://media/1" to "first-photo-bytes".toByteArray(),
        "file:///tmp/second.jpg" to "second-photo-bytes".toByteArray(),
    )

    private fun sources(uri: String): InputStream? = photoBytes[uri]?.inputStream()

    private fun writeArchive(photos: List<String> = photoBytes.keys.toList()): ByteArray =
        BackupArchive.writeToBytes(
            payloadJson = payload,
            createdAtMillis = 1_700_000_000_000L,
            appVersion = "1.0.0 (1)",
            vesselIds = listOf("v1"),
            photoUris = photos,
            photoSources = ::sources,
        )

    @Test
    fun `plain archive round trips payload manifest and photos`() {
        val archive = writeArchive()
        val extracted = LinkedHashMap<String, ByteArray>()

        val contents = BackupArchive.read(ByteArrayInputStream(archive)) { photo, stream ->
            extracted[photo.uri] = stream.readBytes()
            "file:///restored/${photo.entryName.substringAfterLast('/')}"
        }

        requireNotNull(contents)
        assertThat(contents.payloadJson).isEqualTo(payload)
        assertThat(contents.manifest.formatVersion).isEqualTo(BackupArchive.CURRENT_FORMAT_VERSION)
        assertThat(contents.manifest.appVersion).isEqualTo("1.0.0 (1)")
        assertThat(contents.manifest.vesselIds).containsExactly("v1")
        assertThat(contents.manifest.photos.map(BackupPhoto::uri))
            .containsExactlyElementsIn(photoBytes.keys)
        assertThat(extracted.keys).containsExactlyElementsIn(photoBytes.keys)
        for ((uri, bytes) in photoBytes) {
            assertThat(extracted[uri]).isEqualTo(bytes)
        }
        // Every archived photo produced a relink entry, which is what a restore rewrites the
        // records with so a backup opened on a new phone still has its photos.
        assertThat(contents.relinkedPhotoUris.keys).containsExactlyElementsIn(photoBytes.keys)
    }

    @Test
    fun `a plain archive is a real zip with no header`() {
        val archive = writeArchive()
        assertThat(BackupCrypto.isEncrypted(archive)).isFalse()
        // "PK" — it opens in any unzipper, which is the point of leaving it plain.
        assertThat(archive.copyOfRange(0, 4)).isEqualTo(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
    }

    @Test
    fun `a photo whose file has gone is skipped rather than failing the backup`() {
        val archive = BackupArchive.writeToBytes(
            payloadJson = payload,
            createdAtMillis = 0L,
            appVersion = "",
            vesselIds = listOf("v1"),
            photoUris = listOf("content://media/1", "content://media/gone"),
            photoSources = ::sources,
        )
        val contents = requireNotNull(BackupArchive.read(ByteArrayInputStream(archive)))
        assertThat(contents.manifest.photos.map(BackupPhoto::uri)).containsExactly("content://media/1")
        assertThat(contents.payloadJson).isEqualTo(payload)
    }

    @Test
    fun `encrypted archive round trips with the right passphrase`() {
        val archive = writeArchive()
        val sealed = BackupCrypto.encrypt(archive, "north sea 1978".toCharArray())

        assertThat(BackupCrypto.isEncrypted(sealed)).isTrue()
        val opened = requireNotNull(BackupCrypto.decrypt(sealed, "north sea 1978".toCharArray()))
        assertThat(opened).isEqualTo(archive)

        val contents = requireNotNull(BackupArchive.read(ByteArrayInputStream(opened)))
        assertThat(contents.payloadJson).isEqualTo(payload)
        assertThat(contents.manifest.photos).hasSize(photoBytes.size)
    }

    @Test
    fun `a wrong passphrase yields null rather than rubbish`() {
        val sealed = BackupCrypto.encrypt(writeArchive(), "correct horse".toCharArray())
        assertThat(BackupCrypto.decrypt(sealed, "wrong horse".toCharArray())).isNull()
    }

    @Test
    fun `a truncated encrypted file yields null`() {
        val sealed = BackupCrypto.encrypt(writeArchive(), "passphrase".toCharArray())
        val truncated = sealed.copyOfRange(0, sealed.size - 32)
        assertThat(BackupCrypto.decrypt(truncated, "passphrase".toCharArray())).isNull()
    }

    @Test
    fun `two encryptions of the same bytes differ`() {
        val archive = writeArchive()
        val a = BackupCrypto.encrypt(archive, "same".toCharArray())
        val b = BackupCrypto.encrypt(archive, "same".toCharArray())
        // Fresh salt and nonce per file: reusing a GCM nonce under one key is catastrophic.
        assertThat(a).isNotEqualTo(b)
        assertThat(BackupCrypto.decrypt(a, "same".toCharArray())).isEqualTo(archive)
        assertThat(BackupCrypto.decrypt(b, "same".toCharArray())).isEqualTo(archive)
    }

    @Test
    fun `rubbish is not mistaken for an archive`() {
        val junk = "this is a photo of a lifeboat, not a backup".toByteArray()
        assertThat(BackupCrypto.isEncrypted(junk)).isFalse()
        assertThat(BackupArchive.read(ByteArrayInputStream(junk))).isNull()
    }

    @Test
    fun `an archive with no payload entry is refused`() {
        val buffer = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
            zip.write("nothing to see".toByteArray())
            zip.closeEntry()
        }
        assertThat(BackupArchive.read(ByteArrayInputStream(buffer.toByteArray()))).isNull()
    }

    @Test
    fun `a manual backup file name is safe and dated`() {
        val name = com.deckwatch.feature.settings.backup.BackupManager
            .manualBackupFileName("MV Example / Test", 1_700_000_000_000L)
        assertThat(name).startsWith("DeckWatch_MV_EXAMPLE_TEST_BACKUP_")
        assertThat(name).endsWith(".${BackupArchive.EXTENSION}")
        assertThat(name).doesNotContain("/")
    }
}
