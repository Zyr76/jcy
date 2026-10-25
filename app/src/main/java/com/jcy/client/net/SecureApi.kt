package com.jcy.client.net

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
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
    private const val UA = "Myuko Ktor Client"
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
        val fpSecretHex: String,
        val mbase64Table: String,
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
                    val s = Session(gateway, boot, dev, biz,
                        boot.fpAlgo?.hmacSecret.orEmpty(), boot.mbase64Table?.tableStr.orEmpty())
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
            .header("User-Agent", "Myuko Ktor Client")
            .header("User-Agent", "Myuko Ktor Client")
            .header("X-Platform", Config.PLATFORM)
            .header("X-App-Id", Config.API_APP_ID)
            .header("X-Key-Version", keyVersion.toString())
            // 注意：negotiate 不能带 X-Device-Id（会走另一分支，响应无法解密）
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.bytes() ?: ByteArray(0)
            check(resp.isSuccessful) { "negotiate http ${resp.code}: ${String(body)}" }
            val first = body.firstOrNull()?.toInt()?.and(0xFF)
            if (first != 3) {
                // 明文 JSON（通常是业务错误），直接抛出可读信息
                throw IllegalStateException("negotiate plain: ${String(body, Charsets.UTF_8)}")
            }
            val plain = String(master.decrypt(body), Charsets.UTF_8)
            // 响应是 {code,msg,data:{deviceSecret,keyVersion,...}}，必须取 data 层
            val root = json.parseToJsonElement(plain).jsonObject
            val code = root["code"]?.jsonPrimitive?.intOrNull ?: 200
            if (code != 200) throw IllegalStateException("negotiate code=$code msg=${root["msg"]}")
            val dataObj = root["data"]?.jsonObject ?: root
            val info = json.decodeFromString(DeviceSecretInfo.serializer(), dataObj.toString())
            check(info.deviceSecret.isNotEmpty()) { "negotiate: deviceSecret empty" }
            Log.i(TAG, "negotiate ok keyVersion=${info.keyVersion} secretLen=${info.deviceSecret.length}")
            return info
        }
    }

    /** POST 加密 JSON，返回解密后的 JSON 字符串；遇 4020 会话过期自动重协商重试一次。 */
    fun postEncrypted(path: String, bodyJson: String, deviceId: String, adSignature: String? = null): String {
        var attempt = 0
        while (true) {
            val s = ensureSession(deviceId)
            val cipher = s.biz.encryptText(bodyJson)
            val b = Request.Builder()
                .url("${s.gateway}$path?app_id=${Config.API_APP_ID}")
                .post(cipher.toRequestBody(OCTET))
            addCommonHeaders(b, s, deviceId, adSignature)
            val text = http.newCall(b.build()).execute().use { resp ->
                val raw = resp.body?.bytes() ?: ByteArray(0)
                if (!resp.isSuccessful && raw.isEmpty()) throw IllegalStateException("http ${resp.code}")
                val encrypted = resp.header("X-Encrypted") != null || (raw.firstOrNull()?.toInt()?.and(0xFF) == 3)
                val plain = if (encrypted) {
                    runCatching { s.biz.decrypt(raw) }.getOrElse {
                        Log.w(TAG, "decrypt failed, raw len=${raw.size} first=${raw.firstOrNull()?.toInt()?.and(0xFF)} raw=${String(raw, Charsets.UTF_8).take(160)}")
                        raw
                    }
                } else raw
                String(plain, Charsets.UTF_8)
            }
            Log.i(TAG, "POST $path -> ${text.take(200)}")
            if (attempt == 0 && text.contains("\"code\":4020")) {
                Log.w(TAG, "session expired, renegotiating")
                reset(); attempt++; continue
            }
            return text
        }
    }

    /** 与官方一致的完整请求头（UA / 签名 / 设备指纹 / 链路追踪）。 */
    private fun addCommonHeaders(b: Request.Builder, s: Session, deviceId: String, adSignature: String?) {
        b.header("User-Agent", UA)
        b.header("Accept", "application/json")
        b.header("Accept-Charset", "UTF-8")
        b.header("X-Platform", Config.PLATFORM)
        b.header("X-App-Id", Config.API_APP_ID)
        b.header("X-Key-Version", s.device.keyVersion.toString())
        b.header("X-Device-Id", deviceId)
        com.jcy.client.Session.token?.let { if (it.isNotEmpty()) b.header("Authorization", "Bearer $it") }
        if (!adSignature.isNullOrEmpty()) b.header("X-Ad-Signature", adSignature)
        // 请求签名：md5(ts + nonce + appId + signSecret)
        val signSecret = s.bootstrap.signSecret.orEmpty()
        if (signSecret.isNotEmpty()) {
            val nonce = java.util.UUID.randomUUID().toString().replace("-", "")
            val ts = System.currentTimeMillis()
            b.header("X-Timestamp", ts.toString())
            b.header("X-Nonce", nonce)
            b.header("X-Signature", md5("$ts$nonce${Config.API_APP_ID}$signSecret"))
        }
        // 设备指纹（服务端据此生成上游 x-form=android；fp 必须与 deviceId 自洽）
        if (s.fpSecretHex.length == 64 && s.mbase64Table.length == 64) {
            runCatching {
                val fp = DeviceFingerprint.build(deviceId, s.fpSecretHex, s.mbase64Table)
                b.header("X-Device-Fingerprint", fp.value)
                b.header("X-FP-Sign", fp.sign)
                b.header("X-FP-Algo", fp.algo)
            }.onFailure { Log.w(TAG, "fingerprint build failed: ${it.message}") }
        }
        b.header("X-Session-Id", java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16))
        b.header("X-Request-Id", java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16))
        val tid = java.util.UUID.randomUUID().toString().replace("-", "")
        b.header("X-Trace-Id", tid)
        b.header("X-Span-Id", java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16))
        b.header("traceparent", "00-$tid-${java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16)}-01")
    }

    private fun md5(s: String): String {
        val d = java.security.MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(); for (b in d) sb.append(String.format("%02x", b.toInt() and 0xFF)); return sb.toString()
    }

    // ---------------- 账号 ----------------
    data class LoginResult(val ok: Boolean, val token: String? = null, val user: com.jcy.client.Session.User? = null, val message: String? = null)

    private fun plainOrDecrypt(s: Session, raw: ByteArray): String {
        if (raw.isEmpty()) return ""
        return if ((raw.first().toInt() and 0xFF) == 123 || raw.first().toInt() and 0xFF == 32) String(raw, Charsets.UTF_8)
        else runCatching { s.biz.decryptText(raw) }.getOrElse { String(raw, Charsets.UTF_8) }
    }

    /** 登录：/auth/encryptKey(明文响应) -> RSA 加密密码 -> /auth/login */
    fun login(account: String, password: String, deviceId: String): LoginResult {
        val s = ensureSession(deviceId)
        // 1) 取 RSA 公钥（响应为明文 JSON）
        val ekReq = Request.Builder()
            .url("${s.gateway}${Config.PATH_AUTH_ENCRYPT_KEY}?app_id=${Config.API_APP_ID}")
            .post(s.biz.encryptText("""{"account":"$account"}""").toRequestBody(OCTET))
            .header("User-Agent", UA).header("Content-Type", "application/octet-stream")
            .header("X-Platform", Config.PLATFORM).header("X-App-Id", Config.API_APP_ID)
            .header("X-Key-Version", s.device.keyVersion.toString())
            .build()
        val ek = http.newCall(ekReq).execute().use { r ->
            val raw = r.body?.bytes() ?: ByteArray(0)
            Log.i(TAG, "encryptKey http=${r.code} len=${raw.size}")
            plainOrDecrypt(s, raw)
        }
        val pk = json.parseToJsonElement(ek).jsonObject.let { it["data"]?.jsonObject ?: it }["publicKey"]?.toString()?.trim('"')
            ?: return LoginResult(false, message = "获取公钥失败: ${ek.take(120)}")
        val encPwd = runCatching { rsaEncrypt(pk.replace("\\n", "\n"), password) }
            .getOrElse { return LoginResult(false, message = "密码加密失败: ${it.message}") }
        // 2) 登录
        val body = """{"account":"$account","password":"$encPwd","deviceId":"$deviceId","deviceName":"${android.os.Build.BRAND} ${android.os.Build.MODEL}","accountType":null}"""
        val lrReq = Request.Builder()
            .url("${s.gateway}${Config.PATH_AUTH_LOGIN}?app_id=${Config.API_APP_ID}")
            .post(s.biz.encryptText(body).toRequestBody(OCTET))
            .header("User-Agent", UA).header("Content-Type", "application/octet-stream")
            .header("X-Platform", Config.PLATFORM).header("X-App-Id", Config.API_APP_ID)
            .header("X-Key-Version", s.device.keyVersion.toString())
            .build()
        val lt = http.newCall(lrReq).execute().use { r ->
            val raw = r.body?.bytes() ?: ByteArray(0)
            Log.i(TAG, "login http=${r.code} len=${raw.size}")
            plainOrDecrypt(s, raw)
        }
        Log.i(TAG, "login -> ${lt.take(240)}")
        val root = runCatching { json.parseToJsonElement(lt).jsonObject }.getOrElse {
            return LoginResult(false, message = "响应解析失败: ${lt.take(120)}")
        }
        val code = root["code"]?.jsonPrimitive?.intOrNull ?: -1
        if (code != 200) return LoginResult(false, message = "code=$code ${root["msg"]?.toString()?.trim('"').orEmpty()}")
        val d = root["data"]?.jsonObject ?: return LoginResult(false, message = "无 data")
        val tk = d["token"]?.toString()?.trim('"') ?: return LoginResult(false, message = "无 token")
        val u = d["user"]?.jsonObject
        val user = com.jcy.client.Session.User(
            id = u?.get("id")?.toString()?.trim('"').orEmpty(),
            name = u?.get("name")?.toString()?.trim('"').orEmpty(),
            email = u?.get("email")?.toString()?.trim('"').orEmpty(),
            avatar = u?.get("pic")?.toString()?.trim('"').orEmpty(),
            vipLevel = runCatching { u?.get("vipLevel")?.jsonPrimitive?.intOrNull ?: 0 }.getOrDefault(0),
        )
        return LoginResult(true, tk, user)
    }

    /** 注册（沿用 login 的 RSA 流程，字段按官方 RegisterApiRequest）。 */
    fun register(account: String, password: String, deviceId: String): LoginResult {
        val s = ensureSession(deviceId)
        val ek = http.newCall(
            Request.Builder().url("${s.gateway}${Config.PATH_AUTH_ENCRYPT_KEY}?app_id=${Config.API_APP_ID}")
                .post(s.biz.encryptText("""{"account":"$account"}""").toRequestBody(OCTET))
                .header("User-Agent", UA).header("Content-Type", "application/octet-stream")
                .header("X-Platform", Config.PLATFORM).header("X-App-Id", Config.API_APP_ID)
                .header("X-Key-Version", s.device.keyVersion.toString()).build()
        ).execute().use { r -> plainOrDecrypt(s, r.body?.bytes() ?: ByteArray(0)) }
        val pk = json.parseToJsonElement(ek).jsonObject.let { it["data"]?.jsonObject ?: it }["publicKey"]?.toString()?.trim('"')
            ?: return LoginResult(false, message = "获取公钥失败")
        val encPwd = rsaEncrypt(pk.replace("\\n", "\n"), password)
        val body = """{"account":"$account","password":"$encPwd","deviceId":"$deviceId","deviceName":"${android.os.Build.BRAND} ${android.os.Build.MODEL}","accountType":null}"""
        val rt = http.newCall(
            Request.Builder().url("${s.gateway}${Config.PATH_AUTH_REGISTER}?app_id=${Config.API_APP_ID}")
                .post(s.biz.encryptText(body).toRequestBody(OCTET))
                .header("User-Agent", UA).header("Content-Type", "application/octet-stream")
                .header("X-Platform", Config.PLATFORM).header("X-App-Id", Config.API_APP_ID)
                .header("X-Key-Version", s.device.keyVersion.toString()).build()
        ).execute().use { r -> plainOrDecrypt(s, r.body?.bytes() ?: ByteArray(0)) }
        Log.i(TAG, "register -> ${rt.take(240)}")
        val root = runCatching { json.parseToJsonElement(rt).jsonObject }.getOrElse { return LoginResult(false, message = rt.take(120)) }
        val code = root["code"]?.jsonPrimitive?.intOrNull ?: -1
        if (code != 200) return LoginResult(false, message = "code=$code ${root["msg"]?.toString()?.trim('"').orEmpty()}")
        return LoginResult(true, message = "注册成功，请登录")
    }

    private fun rsaEncrypt(pem: String, plain: String): String {
        val b64 = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replace("\\s".toRegex(), "")
        val der = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        val pub = java.security.KeyFactory.getInstance("RSA").generatePublic(java.security.spec.X509EncodedKeySpec(der))
        val c = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, pub)
        return android.util.Base64.encodeToString(c.doFinal(plain.toByteArray()), android.util.Base64.NO_WRAP)
    }

    fun reset() { session = null }
}
