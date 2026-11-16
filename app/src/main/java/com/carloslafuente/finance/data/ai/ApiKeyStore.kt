package com.carloslafuente.finance.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEY_ALIAS = "finance_anthropic_api_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128
private const val GCM_IV_BYTES = 12

/**
 * Keeps the Anthropic API key only as ciphertext in the app's no-backup directory, encrypted with
 * an AES-GCM key that never leaves the Android Keystore. The key is never logged.
 */
class ApiKeyStore(private val context: Context) {
    private val file: File get() = File(context.noBackupFilesDir, "anthropic_api_key.bin")

    suspend fun save(apiKey: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        file.writeBytes(cipher.iv + cipher.doFinal(apiKey.toByteArray()))
    }

    /** Null when nothing is saved or it can no longer be decrypted (for instance after a restore to another phone). */
    suspend fun load(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val blob = file.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, blob.copyOfRange(0, GCM_IV_BYTES)))
            }
            cipher.doFinal(blob.copyOfRange(GCM_IV_BYTES, blob.size)).decodeToString()
        }.getOrNull()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }
}
