package com.jcy.client.net

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class DeviceSecretInfo(
    @SerialName("deviceId") val deviceId: String = "",
    @SerialName("deviceSecret") val deviceSecret: String = "",
    @SerialName("keyVersion") val keyVersion: Int = 0,
    @SerialName("renegotiateAt") val renegotiateAt: Long = 0,
)

@Serializable
private data class NegotiateRequest(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("platform") val platform: String,
)

/**
 * 业务网关客户端：
 * resolve(polyvision) -> bootstrap(masterSecret) -> negotiate(deviceSecret) -> 加密业务请求
 */
object SecureApi {
    private const val TAG = "SecureApi"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val OCTET = "application/octet-stream".toMediaType()
    private val JSON_CT = "application/json".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Session(
        val gateway: String,
        val bootstrap: BootstrapConfig,
        val device: DeviceSecretInfo,
        val biz: EncryptionSdk,
    )

    @Volatile
    private var session: Session? = null

    fun ensureSession(deviceId: String): Session {
        session?.let { return it }
        synchronized(this) {
            session?.let { return it }
            val endpoints = Addressing.resolvePolyvision()
            val errors = StringBuilder()
            for (ep in endpoints) {
                val gateway = ep.baseUrl()
                if (gateway.isEmpty()) continue
                try {
                    val boot = Bootstrap.load(gateway)
                    val master = EncryptionSdk.fromMasterSecret(boot.masterSecret)
                    val dev = negotiate(gateway, deviceId, master, boot.keyVersion)
                    val biz = EncryptionSdk.fromMasterSecret(dev.deviceSecret)
                    val s = Session(gateway, boot, dev, biz)
                    session = s
                    Log.i(TAG, "session ready gateway=$gateway keyVersion=${boot.keyVersion} nodeCount=2")
                    return s
                } catch (t: Throwable) {
                    Log.w(TAG, "gateway $gateway failed: ${t.message}")
                    errors.append("$gateway: ${t.message}\n")
                }
            }
            throw IllegalStateException("all gateways failed:\n$errors")
        }
    }

    private fun negotiate(gateway: String, deviceId: String, master: EncryptionSdk, keyVersion: Int): DeviceSecretInfo {
        val payload = json.encodeToString(NegotiateRequest.serializer(), NegotiateRequest(deviceId, Config.PLATFORM))
        val req = Request.Builder()
            .url("$gateway${Config.PATH_NEGOTIATE}?app_id=${Config.API_APP_ID}")
            .post(payload.toRequestBody(JSON_CT))
            .header("X-Platform", Config.PLATFORM)
            .header("X-App-Id", Config.API_APP_ID)
            .header("X-Key-Version", keyVersion.toString())
            .header("X-Device-Id", deviceId)
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.bytes() ?: ByteArray(0)
            check(resp.isSuccessful) { "negotiate http ${resp.code}: ${String(body)}" }
            // 响应体为 V3 加密的 JSON
            val plain = master.decrypt(body)
            return json.decodeFromString(DeviceSecretInfo.serializer(), String(plain, Charsets.UTF_8))
        }
    }

    /** POST 加密 JSON，返回解密后的 JSON 字符串。 */
    fun postEncrypted(path: String, bodyJson: String, deviceId: String): String {
        val s = ensureSession(deviceId)
        val cipher = s.biz.encryptText(bodyJson)
        val req = Request.Builder()
            .url("${s.gateway}$path?app_id=${Config.API_APP_ID}")
            .post(cipher.toRequestBody(OCTET))
            .header("X-Platform", Config.PLATFORM)
            .header("X-App-Id", Config.API_APP_ID)
            .header("X-Key-Version", s.device.keyVersion.toString())
            .header("X-Device-Id", deviceId)
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful && raw.isEmpty()) throw IllegalStateException("http ${resp.code}")
            val encryptedHeader = resp.header("X-Encrypted")
            val plain = if (encryptedHeader != null || raw.firstOrNull()?.toInt() == 3) {
                runCatching { s.biz.decrypt(raw) }.getOrElse { raw }
            } else raw
            return String(plain, Charsets.UTF_8)
        }
    }

    fun reset() { session = null }
}
