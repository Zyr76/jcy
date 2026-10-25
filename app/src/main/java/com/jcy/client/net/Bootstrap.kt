package com.jcy.client.net

import com.yy.myuko.feature.encryptionsdk.bridge.MagicFingerprintLib
import com.yy.myuko.feature.encryptionsdk.bridge.MagicSdkLib
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BootstrapConfig(
    @SerialName("configVersion") val configVersion: Long = 0,
    @SerialName("keyVersion") val keyVersion: Int = 0,
    @SerialName("masterSecret") val masterSecret: String,
    @SerialName("signSecret") val signSecret: String? = null,
    @SerialName("serverTime") val serverTime: Long = 0,
    @SerialName("fpAlgo") val fpAlgo: FpAlgo? = null,
    @SerialName("mbase64Table") val mbase64Table: MBase64Table? = null,
)

@Serializable
data class FpAlgo(
    @SerialName("algoName") val algoName: String = "",
    @SerialName("hmacSecret") val hmacSecret: String = "",
    @SerialName("version") val version: Int = 0,
)

@Serializable
data class MBase64Table(
    @SerialName("tableStr") val tableStr: String = "",
    @SerialName("version") val version: Int = 0,
)

/**
 * Bootstrap 密钥配置：
 * 1) GET /api/v1/app/client/auth/bootstrap.jpg?appId&sv&_t
 * 2) 解析 JPEG 尾部隐写 (MAGIC 0x89 'P' 'C' 'G')：len(4BE) IV(16) ciphertext(len) HMAC(32)
 * 3) 候选密钥 = nativeBootstrapDeriveCandidateKeys(payload0..3, appId, nowSec)，192B = 3×64B
 * 4) 逐一校验 HMAC-SHA256(key[32:64], iv||ct) 并 AES-256-CBC(key[0:32], iv) 解密出 JSON
 */
object Bootstrap {
    private val TAG = "Bootstrap"
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** 初始化 native 加密 SDK（注册算法链）。 */
    fun initNativeSdk() {
        MagicSdkLib.init(
            Config.ALGO_IDS, Config.MC_ALGO_IDS, Config.KEY_LENGTHS,
            Config.WIRE_CODE_MASK, Config.PAYLOAD_INTEGRITY,
        )
    }

    fun assembleVersion(): Int =
        MagicFingerprintLib.nativeBootstrapAssembleVersion(hexToBytes(Config.PAYLOAD_V_HEX))

    /** 下载并解密 bootstrap 配置。gateway 为 polyvision 端点 base url。 */
    fun load(gateway: String): BootstrapConfig {
        initNativeSdk()
        val sv = assembleVersion()
        val nowSec = System.currentTimeMillis() / 1000
        val url = "$gateway${Config.PATH_BOOTSTRAP_PNG}?appId=${Config.API_APP_ID}&sv=$sv&_t=$nowSec"
        val req = Request.Builder().url(url)
            .header("X-Platform", Config.PLATFORM)
            .header("X-App-Id", Config.API_APP_ID)
            .get().build()
        val bytes = http.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "bootstrap http ${resp.code}" }
            resp.body!!.bytes()
        }
        val stego = parseStego(bytes)
        val candidates = MagicFingerprintLib.nativeBootstrapDeriveCandidateKeys(
            hexToBytes(Config.PAYLOAD0_HEX),
            hexToBytes(Config.PAYLOAD1_HEX),
            hexToBytes(Config.PAYLOAD2_HEX),
            hexToBytes(Config.PAYLOAD3_HEX),
            Config.API_APP_ID,
            nowSec,
        )
        check(candidates.size == 192) { "candidate keys size ${candidates.size}" }
        val ivCt = stego.iv + stego.ciphertext
        for (i in 0 until 3) {
            val cand = candidates.copyOfRange(i * 64, (i + 1) * 64)
            val mac = hmacSha256(cand.copyOfRange(32, 64), ivCt)
            if (!constantTimeEquals(mac, stego.hmac)) continue
            val plain = aes256CbcDecrypt(cand.copyOfRange(0, 32), stego.iv, stego.ciphertext)
            return json.decodeFromString(BootstrapConfig.serializer(), String(plain, Charsets.UTF_8))
        }
        throw IllegalStateException("bootstrap: all candidate keys failed HMAC")
    }

    private data class Stego(val iv: ByteArray, val ciphertext: ByteArray, val hmac: ByteArray) {
        override fun equals(other: Any?) = false
        override fun hashCode() = 0
    }

    private fun parseStego(image: ByteArray): Stego {
        var eoi = -1
        var i = image.size - 2
        while (i >= 0) {
            if (image[i] == 0xFF.toByte() && image[i + 1] == 0xD9.toByte()) { eoi = i; break }
            i--
        }
        require(eoi >= 0) { "bootstrap: not a JPEG (no EOI)" }
        val tail = image.copyOfRange(eoi + 2, image.size)
        require(tail.size >= 56) { "bootstrap: tail too short ${tail.size}" }
        require(tail[0] == 0x89.toByte() && tail[1] == 'P'.code.toByte() && tail[2] == 'C'.code.toByte() && tail[3] == 'G'.code.toByte()) {
            "bootstrap: bad magic"
        }
        val len = ((tail[4].toInt() and 0xFF) shl 24) or ((tail[5].toInt() and 0xFF) shl 16) or
            ((tail[6].toInt() and 0xFF) shl 8) or (tail[7].toInt() and 0xFF)
        require(len in 1..(tail.size - 56)) { "bootstrap: bad payload len $len" }
        val iv = tail.copyOfRange(8, 24)
        val ct = tail.copyOfRange(24, 24 + len)
        val hmac = tail.copyOfRange(24 + len, 24 + len + 32)
        return Stego(iv, ct, hmac)
    }

    internal fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    internal fun aes256CbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }
}
