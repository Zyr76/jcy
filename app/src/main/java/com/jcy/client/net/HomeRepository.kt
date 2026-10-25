package com.jcy.client.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull

data class VideoItem(
    val id: String,
    val name: String,
    val cover: String,
    val description: String,
    val categoryName: String,
)

data class Module(
    val id: String,
    val name: String,
    val layout: String,
    val items: List<VideoItem>,
)

data class VideoDetail(
    val id: String,
    val name: String,
    val cover: String,
    val description: String,
    /** 播放源列表：name -> url */
    val sources: List<PlaySource>,
    /** 线路：playerCode -> playerName */
    val players: List<Pair<String, String>> = emptyList(),
)

data class PlaySource(val name: String, val url: String, val quality: String = "")

object HomeRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val TAG = "HomeRepo"

    private fun str(o: JsonObject, vararg keys: String): String {
        for (k in keys) o[k]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) return it }
        return ""
    }

    private fun itemOf(o: JsonObject): VideoItem = VideoItem(
        id = str(o, "id", "videoId", "vid"),
        name = str(o, "name", "title", "videoName"),
        cover = str(o, "coverUrl", "cover", "pic", "img"),
        description = str(o, "description", "desc", "brief"),
        categoryName = str(o, "categoryName", "category", "typeName"),
    )

    /** 首页视频模块。 */
    fun videoModules(deviceId: String, pageIndex: Int = 1, pageSize: Int = 20): List<Module> {
        val body = """{"pageIndex":$pageIndex,"pageSize":$pageSize}"""
        val resp = SecureApi.postEncrypted(Config.PATH_VIDEO_MODULES, body, deviceId)
        val root = json.parseToJsonElement(resp).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val list = data["list"]?.jsonArray ?: return emptyList()
        return list.mapNotNull { el ->
            val m = el.jsonObject
            val items = m["items"]?.jsonArray?.mapNotNull { runCatching { itemOf(it.jsonObject) }.getOrNull() } ?: emptyList()
            Module(
                id = str(m, "id", "k"),
                name = str(m, "name", "title"),
                layout = str(m, "layout"),
                items = items,
            )
        }.filter { it.items.isNotEmpty() }
    }

    /** 首页推荐（猜你喜欢）。 */
    fun recommend(deviceId: String, pageIndex: Int = 1, pageSize: Int = 40): List<VideoItem> {
        val body = """{"pageIndex":$pageIndex,"pageSize":$pageSize}"""
        val resp = SecureApi.postEncrypted(Config.PATH_HOME_RECOMMEND, body, deviceId)
        val root = json.parseToJsonElement(resp).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val gyl = data["guessYouLike"]?.jsonObject
        val arr = gyl?.get("items")?.jsonArray ?: gyl?.get("list")?.jsonArray ?: data["list"]?.jsonArray
        return arr?.mapNotNull { runCatching { itemOf(it.jsonObject) }.getOrNull() } ?: emptyList()
    }

    /** 视频详情 + 播放源。 */
    fun videoDetail(deviceId: String, videoId: String): VideoDetail {
        val body = """{"videoId":"$videoId"}"""
        val resp = SecureApi.postEncrypted(Config.PATH_VIDEO_DETAIL, body, deviceId)
        val root = json.parseToJsonElement(resp).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val sources = mutableListOf<PlaySource>()
        fun collect(obj: JsonObject) {
            val arr = obj["playSources"]?.jsonArray ?: obj["sources"]?.jsonArray ?: obj["players"]?.jsonArray ?: return
            arr.forEach { s ->
                val so = s.jsonObject
                val url = str(so, "url", "playUrl", "src", "m3u8")
                if (url.isNotEmpty()) sources.add(PlaySource(str(so, "name", "title", "line"), url, str(so, "quality", "label")))
            }
        }
        collect(data)
        val players = mutableListOf<Pair<String, String>>()
        data["player"]?.jsonArray?.forEach { p ->
            runCatching {
                val po = p.jsonObject
                val code = str(po, "playerCode")
                if (code.isNotEmpty()) players.add(code to str(po, "playerName", "name"))
            }
        }
        return VideoDetail(
            id = str(data, "id", "videoId"),
            name = str(data, "title", "name"),
            cover = str(data, "coverUrl", "cover"),
            description = str(data, "description", "desc"),
            sources = sources,
            players = players,
        )
    }

    fun search(deviceId: String, keyword: String, pageIndex: Int = 1, pageSize: Int = 30): List<VideoItem> {
        val body = """{"keyword":"$keyword","pageIndex":$pageIndex,"pageSize":$pageSize}"""
        val resp = SecureApi.postEncrypted(Config.PATH_VIDEO_SEARCH, body, deviceId)
        val root = json.parseToJsonElement(resp).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val arr = data["list"]?.jsonArray ?: data["items"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { runCatching { itemOf(it.jsonObject) }.getOrNull() }
    }
}

/** 剧集与播放地址解析（resolve 系列接口）。 */
object ResolveRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Episode(val num: Int, val name: String, val code: String, val isVip: Int)

    private fun str(o: kotlinx.serialization.json.JsonObject, vararg keys: String): String {
        for (k in keys) o[k]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) return it }
        return ""
    }

    fun episodes(deviceId: String, videoId: String, code: String): List<Episode> {
        val body = """{"videoId":"$videoId","playerCode":"$code"}"""
        val resp = SecureApi.postEncrypted(Config.PATH_RESOLVE_EPISODES, body, deviceId)
        val root = json.parseToJsonElement(resp).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val arr = data["episodes"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            runCatching {
                val o = e.jsonObject
                Episode(
                    num = o["episodeNum"]?.jsonPrimitive?.intOrNull ?: 0,
                    name = str(o, "displayName", "episodeName"),
                    code = str(o, "code"),
                    isVip = o["isVip"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }.getOrNull()
        }
    }

        /** 免广告换取播放地址：getConfig->claimReward 拿 adToken，再 exchange。 */
    fun exchange(deviceId: String, videoId: String, code: String, episodeNum: Int, quality: String, exchangeToken: String): Pair<String?, String> {
        val adToken = runCatching { claimAdToken(deviceId, "player_pre_roll") }.getOrNull()
        val body = """{"videoId":"$videoId","code":"$code","episodeNum":$episodeNum,"quality":"$quality","adToken":${if (adToken == null) "null" else "\"$adToken\""},"adSignature":"","exchangeToken":"$exchangeToken"}"""
        val resp = SecureApi.postEncrypted(Config.PATH_RESOLVE_EXCHANGE, body, deviceId)
        val root = json.parseToJsonElement(resp).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val direct = str(data, "playUrl", "url")
        if (direct.startsWith("http")) return Pair(direct, "")
        val msg = str(root, "msg")
        val clientParse = data["isClientParse"]?.jsonPrimitive?.booleanOrNull == true
        val orig = str(data, "originalUrl")
        return Pair(null, if (clientParse) "客户端解析模式(需Lua): orig=$orig" else msg)
    }

    /** 免广告获取 adToken（getConfig 的 nonce -> claimReward）。 */
    fun claimAdToken(deviceId: String, slotCode: String): String {
        val cfg = SecureApi.postEncrypted(Config.PATH_AD_GET_CONFIG, """{"slotCode":"$slotCode"}""", deviceId)
        val nonce = str(json.parseToJsonElement(cfg).jsonObject.let { (it["data"]?.jsonObject ?: it) }, "nonce")
        require(nonce.isNotEmpty()) { "no nonce" }
        val claim = SecureApi.postEncrypted(Config.PATH_AD_CLAIM_REWARD, """{"nonce":"$nonce","slotCode":"$slotCode","taskCode":null}""", deviceId)
        val d = json.parseToJsonElement(claim).jsonObject.let { (it["data"]?.jsonObject ?: it) }
        return str(d, "adToken")
    }

    /** 取清晰度列表与 exchangeToken。 */
    fun qualities(deviceId: String, videoId: String, code: String, episodeNum: Int): Pair<String, String> {
        val body = """{"videoId":"$videoId","code":"$code","episodeNum":$episodeNum,"qualities":[]}"""
        val resp = SecureApi.postEncrypted(Config.PATH_RESOLVE_QUALITIES, body, deviceId)
        val root = json.parseToJsonElement(resp).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val et = str(data, "exchangeToken")
        val q = data["qualities"]?.jsonArray?.firstOrNull()?.jsonObject?.let { str(it, "quality") } ?: ""
        return Pair(et, q)
    }

    private fun findUrl(raw: ByteArray): String? {
        val s = String(raw, Charsets.UTF_8)
        val m = Regex("https?://[^\"'\\\\\\s]+").find(s)
        return m?.value
    }
}
