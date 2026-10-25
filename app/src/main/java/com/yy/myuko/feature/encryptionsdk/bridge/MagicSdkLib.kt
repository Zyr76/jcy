package com.yy.myuko.feature.encryptionsdk.bridge

/**
 * JNI 绑定到官方 libmagic_cipher.so。
 * 类名/包名/方法签名必须与导出符号 Java_com_yy_myuko_feature_encryptionsdk_bridge_* 完全一致。
 */
object MagicCipherLib {
    init {
        System.loadLibrary("magic_cipher")
    }
}

class MagicSdkLib private constructor() {
    companion object {
        init {
            System.loadLibrary("magic_cipher")
        }

        @JvmStatic external fun init(algoIds: ByteArray, mcAlgoIds: ByteArray, keyLengths: ShortArray, wireCodeMask: ByteArray, payloadIntegrity: ByteArray)

        @JvmStatic external fun decrypt(secret: ByteArray, payload: ByteArray): ByteArray

        @JvmStatic external fun decryptV3(secret: ByteArray, payload: ByteArray): ByteArray

        @JvmStatic external fun encryptV3(secret: ByteArray, plaintext: ByteArray, timestamp: Long, nodeCount: Int): ByteArray

        @JvmStatic external fun encryptV3WithCode(secret: ByteArray, plaintext: ByteArray, code: ByteArray, timestamp: Long): ByteArray

        @JvmStatic external fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

        @JvmStatic external fun sha256(data: ByteArray): ByteArray

        @JvmStatic external fun deriveCode(secret: ByteArray, timestamp: Long, nodeCount: Int): ByteArray

        @JvmStatic external fun deriveWireMask(secret: ByteArray, timestamp: Long): ByteArray

        @JvmStatic external fun secureRandom(length: Int): ByteArray

        @JvmStatic external fun slotFromTime(timestamp: Long): Long
    }
}

class MagicFingerprintLib private constructor() {
    companion object {
        init {
            System.loadLibrary("magic_cipher")
        }

        @JvmStatic external fun nativeBootstrapDeriveCandidateKeys(p0: ByteArray, p1: ByteArray, p2: ByteArray, p3: ByteArray, appId: String, timestamp: Long): ByteArray

        @JvmStatic external fun nativeBootstrapAssembleVersion(payloadV: ByteArray): Int
    }
}
