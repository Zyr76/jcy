package com.jcy.client.net

import com.yy.myuko.feature.encryptionsdk.bridge.MagicSdkLib

/**
 * 复刻官方 EncryptionSDK（V3 协议）：
 * - encrypt: native encryptV3(secret, plaintext, nowMs, nodeCount)
 * - decrypt: payload[0] 为版本字节（3=V3），native decryptV3(secret, payload)
 */
class EncryptionSdk(private val secret: ByteArray, private val nodeCount: Int = 2) {

    fun encrypt(plaintext: ByteArray): ByteArray =
        MagicSdkLib.encryptV3(secret, plaintext, System.currentTimeMillis(), nodeCount)

    fun decrypt(payload: ByteArray): ByteArray {
        require(payload.size >= 3) { "payload too short" }
        val version = payload[0].toInt()
        return when (version) {
            3 -> MagicSdkLib.decryptV3(secret, payload)
            else -> MagicSdkLib.decrypt(secret, payload)
        }
    }

    fun encryptText(text: String): ByteArray = encrypt(text.toByteArray(Charsets.UTF_8))

    fun decryptText(payload: ByteArray): String = String(decrypt(payload), Charsets.UTF_8)

    companion object {
        fun fromMasterSecret(masterSecret: String): EncryptionSdk =
            EncryptionSdk(masterSecret.toByteArray(Charsets.UTF_8))
    }
}
