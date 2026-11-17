package com.benji.partybell

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    const val NONCE_SIZE = 16
    private const val HMAC = "HmacSHA256"
    private val proofLabel = "partybell phone".toByteArray()
    private val keyLabel = "partybell key".toByteArray()
    private val random = SecureRandom()

    fun nonce(): ByteArray = ByteArray(NONCE_SIZE).also(random::nextBytes)

    fun proof(secret: ByteArray, serverNonce: ByteArray, phoneNonce: ByteArray): ByteArray =
        hmac(secret, proofLabel + serverNonce + phoneNonce)

    fun sessionKey(secret: ByteArray, serverNonce: ByteArray, phoneNonce: ByteArray): ByteArray {
        val prk = hmac(serverNonce + phoneNonce, secret)
        return hmac(prk, keyLabel + byteArrayOf(1))
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance(HMAC).run {
            init(SecretKeySpec(key, HMAC))
            doFinal(data)
        }
}

class Opener(key: ByteArray) {
    private companion object {
        const val NONCE_SIZE = 12
        const val TAG_BITS = 128
    }

    private val key = SecretKeySpec(key, "AES")
    private var counter = 0L

    fun open(sealed: ByteArray): ByteArray? {
        val nonce = ByteBuffer.allocate(NONCE_SIZE).putInt(0).putLong(counter).array()
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.doFinal(sealed).also { counter++ }
        } catch (_: GeneralSecurityException) {
            null
        }
    }
}
