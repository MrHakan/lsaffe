package com.deckwatch.feature.settings.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The optional passphrase protection of MASTER_PROMPT §18 — *"optionally passphrase-protected"*.
 *
 * ### The container
 *
 * ```
 *  0..3    magic      "DWBE"            — DeckWatch Backup, Encrypted
 *  4       version    0x01
 *  5..20   salt       16 random bytes   — PBKDF2 salt, fresh per file
 * 21..32   iv         12 random bytes   — GCM nonce, fresh per file
 * 33..     payload    AES-256/GCM ciphertext with the 128-bit tag appended
 * ```
 *
 * A **plain** backup has no header at all: it is the zip, byte for byte, so it opens in any
 * unzipper. That is deliberate — C10 says data must never be silently lost, and an officer who
 * forgets which of their `.dwbackup` files had a passphrase can always tell by looking, and can
 * always read the plain one with tools that will still exist in ten years. [isEncrypted]
 * distinguishes them by the magic, so restore never has to ask.
 *
 * ### The parameters, and why
 *
 * * **PBKDF2-HMAC-SHA256, 210 000 iterations** — the OWASP 2023 recommendation for PBKDF2-SHA256.
 *   `SecretKeyFactory` has offered this algorithm since API 26, which is the app's minSdk (C4), so
 *   no compatibility shim is needed. Argon2/scrypt would be stronger but need a native dependency,
 *   and C3 keeps the APK free of anything it does not have to carry.
 * * **AES-256/GCM** — authenticated encryption. The tag is what makes [decrypt] able to return
 *   null for "wrong passphrase" instead of handing back plausible rubbish that would then be
 *   imported over the officer's real register.
 * * **Fresh salt and nonce per file.** A GCM nonce reused under one key is catastrophic; deriving
 *   both from the file's own random bytes means two backups taken a second apart with the same
 *   passphrase share nothing.
 *
 * The passphrase is taken as a `CharArray` and zeroed after key derivation, so it does not linger
 * in the immutable-`String` pool waiting for a heap dump.
 */
object BackupCrypto {

    /** Magic bytes at the head of an encrypted backup: `DWBE`. */
    val MAGIC: ByteArray = byteArrayOf(0x44, 0x57, 0x42, 0x45)

    const val FORMAT_VERSION: Byte = 1
    const val SALT_BYTES: Int = 16
    const val IV_BYTES: Int = 12
    const val GCM_TAG_BITS: Int = 128
    const val KEY_BITS: Int = 256

    /** OWASP 2023 guidance for PBKDF2-HMAC-SHA256. */
    const val ITERATIONS: Int = 210_000

    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"

    private val HEADER_BYTES = MAGIC.size + 1 + SALT_BYTES + IV_BYTES

    /** True when [blob] carries the encrypted-container header. */
    fun isEncrypted(blob: ByteArray): Boolean =
        blob.size > HEADER_BYTES && MAGIC.indices.all { blob[it] == MAGIC[it] }

    /** Wrap [plain] in the container above. [passphrase] is zeroed before returning. */
    fun encrypt(
        plain: ByteArray,
        passphrase: CharArray,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plain)

        val out = ByteArray(HEADER_BYTES + ciphertext.size)
        MAGIC.copyInto(out, 0)
        out[MAGIC.size] = FORMAT_VERSION
        salt.copyInto(out, MAGIC.size + 1)
        iv.copyInto(out, MAGIC.size + 1 + SALT_BYTES)
        ciphertext.copyInto(out, HEADER_BYTES)
        return out
    }

    /**
     * Unwrap [blob].
     *
     * @return the plaintext, or **null** when the passphrase is wrong, the file is truncated, or
     *   the authentication tag does not verify. One null for every failure on purpose: the caller
     *   shows "wrong passphrase or damaged file", and distinguishing the two would tell an attacker
     *   with the file which half they got right.
     */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray? {
        if (!isEncrypted(blob)) return null
        if (blob[MAGIC.size] != FORMAT_VERSION) return null
        val salt = blob.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + SALT_BYTES)
        val iv = blob.copyOfRange(MAGIC.size + 1 + SALT_BYTES, HEADER_BYTES)
        val ciphertext = blob.copyOfRange(HEADER_BYTES, blob.size)
        return runCatching {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        }.getOrNull()
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
            SecretKeySpec(bytes, KEY_ALGORITHM)
        } finally {
            spec.clearPassword()
        }
    }
}
