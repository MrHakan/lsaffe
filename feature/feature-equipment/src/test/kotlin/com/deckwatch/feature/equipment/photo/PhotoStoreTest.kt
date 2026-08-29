package com.deckwatch.feature.equipment.photo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where a capture lands and what a stored URI resolves back to — §7.6.
 *
 * Only one test calls [PhotoStore.uriFor]: `FileProvider` caches its path strategy per authority
 * for the life of the class loader, while Robolectric hands every test a different `filesDir`, so
 * a second caller would be checked against the first test's directory. The rest build the URI the
 * provider would have produced, which is also the sharper test of the parsing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `each item gets its own directory under the app's own files`() {
        val dir = PhotoStore.directoryFor(context, "equipment-1")

        assertThat(dir.exists()).isTrue()
        assertThat(dir.canonicalPath).startsWith(File(context.filesDir, "photos").canonicalPath)
    }

    @Test
    fun `a path separator in an id cannot escape the photo directory`() {
        val dir = PhotoStore.directoryFor(context, "../../etc")

        assertThat(dir.canonicalPath).startsWith(File(context.filesDir, "photos").canonicalPath)
    }

    @Test
    fun `two captures in the same millisecond get different files`() {
        val first = PhotoStore.newPhotoFile(context, "equipment-1", atMillis = CAPTURE_MILLIS)
        first.writeBytes(ByteArray(1))
        val second = PhotoStore.newPhotoFile(context, "equipment-1", atMillis = CAPTURE_MILLIS)

        assertThat(second.path).isNotEqualTo(first.path)
    }

    @Test
    fun `the capture uri points at the file the camera was given`() {
        val file = PhotoStore.newPhotoFile(context, "equipment-1", atMillis = CAPTURE_MILLIS)
        file.writeBytes(ByteArray(3))

        val uri = PhotoStore.uriFor(context, file)

        assertThat(uri.authority).isEqualTo(PhotoStore.authority(context))
        assertThat(uri.pathSegments)
            .containsExactly(PhotoStore.ROOT_NAME, "equipment-1", file.name)
            .inOrder()
    }

    @Test
    fun `a stored uri resolves back to the file, keeping the item it belongs to`() {
        val file = PhotoStore.newPhotoFile(context, "equipment-1", atMillis = CAPTURE_MILLIS)
        file.writeBytes(ByteArray(3))
        // A second item holds a file of the same name: resolving on the name alone would confuse them.
        val other = File(PhotoStore.directoryFor(context, "equipment-2"), file.name)
        other.writeBytes(ByteArray(3))

        val resolved = PhotoStore.fileFor(context, uriOf("equipment-1", file.name))

        assertThat(resolved?.canonicalPath).isEqualTo(file.canonicalPath)
    }

    @Test
    fun `a uri from somewhere else resolves to nothing`() {
        assertThat(PhotoStore.fileFor(context, "content://other.app/files/1.jpg")).isNull()
    }

    @Test
    fun `deleting is idempotent, because a record outlives its file`() {
        val file = PhotoStore.newPhotoFile(context, "equipment-1", atMillis = CAPTURE_MILLIS)
        file.writeBytes(ByteArray(3))
        val uri = uriOf("equipment-1", file.name)

        assertThat(PhotoStore.delete(context, uri)).isTrue()
        assertThat(PhotoStore.fileFor(context, uri)).isNull()
        assertThat(PhotoStore.delete(context, uri)).isTrue()
    }

    @Test
    fun `a photo whose file is gone yields no thumbnail instead of throwing`() {
        // Robolectric shadows BitmapFactory, so what is provable here is the lookup, not the
        // decode: a URI with nothing behind it must come back as null, never as an exception.
        val thumbnail = PhotoStore.decodeThumbnail(
            context,
            uriOf("equipment-1", "never-written.jpg"),
            PhotoStore.THUMBNAIL_MAX_EDGE,
        )

        assertThat(thumbnail).isNull()
    }

    /** The URI the provider produces for a photo, without going through the provider itself. */
    private fun uriOf(equipmentId: String, fileName: String): String =
        "content://${PhotoStore.authority(context)}/${PhotoStore.ROOT_NAME}/$equipmentId/$fileName"

    private companion object {
        const val CAPTURE_MILLIS = 1_700_000_000_000L
    }
}
