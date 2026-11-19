package com.benji.partybell

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SessionKeys(val toPhone: ByteArray, val toPc: ByteArray)

class Handshake(val nonce: ByteArray, val ephemeral: ByteArray, val proof: ByteArray, val keys: SessionKeys)

object Crypto {
    const val NONCE_SIZE = 16
    private const val HMAC = "HmacSHA256"
    private const val CURVE = "secp256r1"
    private val proofLabel = "partybell phone".toByteArray()
    private val toPhoneLabel = "partybell to phone".toByteArray()
    private val toPcLabel = "partybell to pc".toByteArray()
    private val random = SecureRandom()

    fun handshake(secret: ByteArray, serverNonce: ByteArray, serverKey: ByteArray): Handshake? = try {
        val own = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec(CURVE), random)
            generateKeyPair()
        }
        val shared = KeyAgreement.getInstance("ECDH").run {
            init(own.private)
            doPhase(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(serverKey)), true)
            generateSecret()
        }
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val ephemeral = own.public.encoded
        val salt = serverNonce + nonce + serverKey + ephemeral
        Handshake(nonce, ephemeral, hmac(secret, proofLabel + salt), sessionKeys(secret + shared, salt))
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    private fun sessionKeys(keyMaterial: ByteArray, salt: ByteArray): SessionKeys {
        val prk = hmac(salt, keyMaterial)
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
