package com.jcy.client.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class ResolveResponse(val code: Int, val message: String? = null, val data: ResolveData? = null)

@Serializable
data class ResolveData(
    val schema: String? = null,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("app_id") val appId: String? = null,
    @SerialName("config_version") val configVersion: String? = null,
    val endpoints: Map<String, List<Endpoint>> = emptyMap(),
)

@Serializable
data class Endpoint(
    val protocol: String,
    val host: String? = null,
    val ip: String? = null,
    val port: Int,
    val priority: Int = 0,
    val weight: Int = 0,
) {
    /** 规范化 base url，优先 https 域名。 */
    fun baseUrl(): String {
        val h = host ?: ip ?: return ""
        val scheme = if (protocol == "https") "https" else "http"
        return if ((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) "$scheme://$h" else "$scheme://$h:$port"
    }
}

/** 地址分发：GET {bootstrap}/api/v1/resolve?app_id=... 明文可用。 */
object Addressing {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: List<Endpoint>? = null

    /** 返回名为 polyvision 的业务网关端点。 */
    fun resolvePolyvision(force: Boolean = false): List<Endpoint> {
        cached?.let { if (!force) return it }
        var lastErr: Throwable? = null
        for (bootstrap in Config.ADDRESSING_BOOTSTRAP_HTTP) {
            try {
                val url = "$bootstrap${Config.PATH_RESOLVE}?app_id=${Config.ADDRESSING_APP_ID}"
                val req = Request.Builder().url(url).get().build()
                http.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val parsed = json.decodeFromString(ResolveResponse.serializer(), body)
                    val list = parsed.data?.endpoints?.get("polyvision").orEmpty()
                        .sortedBy { it.priority }
                    if (list.isNotEmpty()) {
                        cached = list
                        return list
                    }
                    lastErr = IllegalStateException("resolve empty: $body")
                }
            } catch (t: Throwable) {
                lastErr = t
            }
        }
        throw IllegalStateException("resolve failed", lastErr)
    }
}
