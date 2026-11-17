package com.benji.partybell

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SessionKeys(val toPhone: ByteArray, val toPc: ByteArray)

object Crypto {
    const val NONCE_SIZE = 16
    private const val HMAC = "HmacSHA256"
    private val proofLabel = "partybell phone".toByteArray()
    private val toPhoneLabel = "partybell to phone".toByteArray()
    private val toPcLabel = "partybell to pc".toByteArray()
    private val random = SecureRandom()

    fun nonce(): ByteArray = ByteArray(NONCE_SIZE).also(random::nextBytes)

    fun proof(secret: ByteArray, serverNonce: ByteArray, phoneNonce: ByteArray): ByteArray =
        hmac(secret, proofLabel + serverNonce + phoneNonce)

    fun sessionKeys(secret: ByteArray, serverNonce: ByteArray, phoneNonce: ByteArray): SessionKeys {
        val prk = hmac(serverNonce + phoneNonce, secret)
        return SessionKeys(hmac(prk, toPhoneLabel + byteArrayOf(1)), hmac(prk, toPcLabel + byteArrayOf(1)))
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance(HMAC).run {
            init(SecretKeySpec(key, HMAC))
            doFinal(data)
        }
}

class Sealer(keys: SessionKeys) {
    private companion object {
        const val NONCE_SIZE = 12
        const val TAG_BITS = 128
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val openKey = SecretKeySpec(keys.toPhone, "AES")
    private val sealKey = SecretKeySpec(keys.toPc, "AES")
    private var openCounter = 0L
    private var sealCounter = 0L

    fun open(sealed: ByteArray): ByteArray? = try {
        Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, openKey, GCMParameterSpec(TAG_BITS, nonce(openCounter)))
            doFinal(sealed)
        }.also { openCounter++ }
    } catch (_: GeneralSecurityException) {
        null
    }

    @Synchronized
    fun seal(plain: ByteArray): ByteArray = Cipher.getInstance(TRANSFORMATION).run {
        init(Cipher.ENCRYPT_MODE, sealKey, GCMParameterSpec(TAG_BITS, nonce(sealCounter++)))
        doFinal(plain)
    }

    private fun nonce(counter: Long) = ByteBuffer.allocate(NONCE_SIZE).putInt(0).putLong(counter).array()
}
