package com.jcy.client.net

import android.os.Build
import com.yy.myuko.feature.encryptionsdk.bridge.MagicFingerprintLib
import kotlinx.serialization.json.Json

/**
 * 设备指纹（X-Device-Fingerprint / X-FP-Sign / X-FP-Algo）。
 *
 * DeviceContext 字段与顺序必须和官方一致（已通过对官方抓包指纹解密验证）：
 * fp,dn,dm,os,osv,av,avc,pkg,asig,hv,pt,dt,sw,sh,lang,tz,net,ts,bi
 *
 * 生成方式（官方 DeviceFingerprintSigner.sign）：
 *   header = mbase64(table, maes(secret[0:16], secret[16:32], json, true), true)
 *   sign   = hex(hmacSha256(secret, header))
 */
object DeviceFingerprint {

    // 伪装成官方包的标识（服务端会按 pkg/asig 校验正版）
    const val OFFICIAL_PKG = "com.dskiow.cjsq.p1"
    const val OFFICIAL_ASIG = "de17140c22b492a9e38d188c675a185d9d67e1607342f03ed0a181d9f3696e74"
    private const val APP_VER = "1.0.11"
    private const val APP_VER_CODE = 112
    // bi 里带不可见字符（ZWSP / WORD JOINER），与官方一致
    private const val BUILD_ID = "\u200b\u200b\u200b\u20601.0.11-112\u200b\u200b\u200b\u200b"

    private val json = Json { encodeDefaults = false }

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun hexLower(b: ByteArray): String {
        val sb = StringBuilder()
        for (x in b) sb.append(String.format("%02x", x.toInt() and 0xFF))
        return sb.toString()
    }

    data class Header(val value: String, val sign: String, val algo: String)

    /** secret: BootstrapConfig.fpAlgo.hmacSecret(hex) / table: BootstrapConfig.mbase64Table.tableStr */
    fun build(deviceId: String, hmacSecretHex: String, tableStr: String): Header {
        val dm = "${Build.BRAND} ${Build.MODEL}".trim()
        val lang = runCatching {
            val l = java.util.Locale.getDefault()
            if (l.script.isNullOrEmpty()) "${l.language}-${l.country}" else "${l.language}-${l.script}-${l.country}"
        }.getOrDefault("zh-Hans-CN")
        val tz = java.util.TimeZone.getDefault().id
        val ctx = buildString {
            append("{\"fp\":\"").append(deviceId).append("\",")
            append("\"dn\":\"").append(dm).append("\",")
            append("\"dm\":\"").append(dm).append("\",")
            append("\"os\":\"Android\",")
            append("\"osv\":\"").append(Build.VERSION.RELEASE).append("\",")
            append("\"av\":\"").append(APP_VER).append("\",")
            append("\"avc\":").append(APP_VER_CODE).append(",")
            append("\"pkg\":\"").append(OFFICIAL_PKG).append("\",")
            append("\"asig\":\"").append(OFFICIAL_ASIG).append("\",")
            append("\"hv\":\"").append(APP_VER).append("\",")
            append("\"pt\":\"android\",")
            append("\"dt\":1,")
            append("\"sw\":").append(resWidth()).append(",")
            append("\"sh\":").append(resHeight()).append(",")
            append("\"lang\":\"").append(lang).append("\",")
            append("\"tz\":\"").append(tz).append("\",")
            append("\"net\":\"wifi\",")
            append("\"ts\":").append(System.currentTimeMillis()).append(",")
            append("\"bi\":\"").append(BUILD_ID).append("\"}")
        }
        val secret = hex(hmacSecretHex)
        require(secret.size == 32) { "hmacSecret 必须 32 字节" }
        val k1 = secret.copyOfRange(0, 16)
        val k2 = secret.copyOfRange(16, 32)
        val table = tableStr.toByteArray(Charsets.UTF_8)
        require(table.size == 64) { "mbase64Table 必须 64 字节" }
        val maes = MagicFingerprintLib.maesProcess(k1, k2, ctx.toByteArray(Charsets.UTF_8), true)
        val header = String(MagicFingerprintLib.mbase64Process(table, maes, true), Charsets.UTF_8)
        val sign = hexLower(Bootstrap.hmacSha256(secret, header.toByteArray(Charsets.UTF_8)))
        return Header(header, sign, "1")
    }

    private fun resWidth(): Int = screen()[0]
    private fun resHeight(): Int = screen()[1]

    private fun screen(): IntArray {
        return runCatching {
            val ctx = com.jcy.client.JcyApp.instance
            val dm = ctx.resources.displayMetrics
            intArrayOf(dm.widthPixels, dm.heightPixels)
        }.getOrDefault(intArrayOf(1080, 2400))
    }
}
