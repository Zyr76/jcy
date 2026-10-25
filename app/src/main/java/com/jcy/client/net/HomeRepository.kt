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

    private fun okData(resp: String): JsonObject {
        val root = json.parseToJsonElement(resp).jsonObject
        val code = root["code"]?.jsonPrimitive?.intOrNull ?: 200
        if (code != 200) throw IllegalStateException("接口错误 $code: ${str(root, "msg")}")
        return root["data"]?.jsonObject ?: throw IllegalStateException("响应无 data: ${resp.take(200)}")
    }

    /** 首页视频模块。 */
    fun videoModules(deviceId: String, pageIndex: Int = 1, pageSize: Int = 20): List<Module> {
        val body = """{"pageIndex":$pageIndex,"pageSize":$pageSize}"""
        val resp = SecureApi.postEncrypted(Config.PATH_VIDEO_MODULES, body, deviceId)
        val data = okData(resp)
        val list = data["list"]?.jsonArray ?: throw IllegalStateException("modules 无 list: ${resp.take(200)}")
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
        val data = okData(resp)
        val gyl = data["guessYouLike"]?.jsonObject
        val arr = gyl?.get("items")?.jsonArray ?: gyl?.get("list")?.jsonArray ?: data["list"]?.jsonArray
        return arr?.mapNotNull { runCatching { itemOf(it.jsonObject) }.getOrNull() } ?: emptyList()
    }

    /** 视频详情 + 播放源。 */
    fun videoDetail(deviceId: String, videoId: String): VideoDetail {
        val body = """{"videoId":"$videoId"}"""
        val resp = SecureApi.postEncrypted(Config.PATH_VIDEO_DETAIL, body, deviceId)
        val data = okData(resp)
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

        /**
     * 完整解析播放地址（复刻官方 Lua 解析逻辑）：
     * exchange -> originalUrl；sign(get_api_url) -> 上游 api_url+headers；
     * httpGet -> 密文；sign(decrypt_response) -> 播放 url。
     */
    fun resolvePlayUrl(deviceId: String, videoId: String, code: String, episodeNum: Int): Pair<String?, String> {
        val (et, q) = qualities(deviceId, videoId, code, episodeNum)
        if (et.isEmpty()) return Pair(null, "qualities 无 exchangeToken")
        val adToken = runCatching { claimAdToken(deviceId, "player_pre_roll") }.getOrNull()
        val exBody = """{"videoId":"$videoId","code":"$code","episodeNum":$episodeNum,"quality":"$q","adToken":${if (adToken == null) "null" else "\"$adToken\""},"adSignature":"","exchangeToken":"$et"}"""
        val exResp = SecureApi.postEncrypted(Config.PATH_RESOLVE_EXCHANGE, exBody, deviceId, adToken)
        val exData = json.parseToJsonElement(exResp).jsonObject.let { it["data"]?.jsonObject ?: it }
        str(exData, "playUrl", "url").takeIf { it.startsWith("http") }?.let { return Pair(it, "") }
        val orig = str(exData, "originalUrl")
        if (orig.isEmpty()) return Pair(null, str(json.parseToJsonElement(exResp).jsonObject, "msg"))
        // 1) get_api_url
        // 官方上游 url 参数是裸值：url=<originalUrl>&t=<ts>，因此 params 必须传字符串
        val s1 = sign(deviceId, code, episodeNum, "get_api_url", kotlinx.serialization.json.JsonPrimitive(orig).toString(), adToken)
        val signObj = json.parseToJsonElement(s1).jsonObject.let { it["data"]?.jsonObject ?: it }["sign"]?.jsonObject
            ?: return Pair(null, "sign(get_api_url) 无结果: $s1")
        val apiUrl = str(signObj, "api_url").replace("\\u0026", "&")
        val headers = signObj["headers"]?.jsonObject
        if (apiUrl.isEmpty()) return Pair(null, "api_url empty")
        // 2) httpGet
        val up = okhttp3.OkHttpClient.Builder().build()
        val reqB = okhttp3.Request.Builder().url(apiUrl)
        headers?.forEach { (k, v) -> runCatching { reqB.header(k, v.jsonPrimitive.contentOrNull ?: "") } }
        val raw = up.newCall(reqB.build()).execute().use { it.body?.string().orEmpty() }
        if (raw.isEmpty()) return Pair(null, "upstream empty")
        // 3) 上游可能直接返回明文 JSON（x-form=android 时），否则走 server_sign 解密
        val plain = if (raw.trimStart().startsWith("{")) raw
        else sign(deviceId, code, episodeNum, "decrypt_response", kotlinx.serialization.json.JsonPrimitive(raw).toString(), adToken)
        val pj = json.parseToJsonElement(plain).jsonObject
        val pd = pj["data"]?.jsonObject ?: pj

        // 格式 A：官方明文 {"data":{"playAddr":[{title,desc,vcodec,addr,m3u8FileDomain,format}]}}
        val playAddr = pd["playAddr"]?.jsonArray
        if (playAddr != null && playAddr.isNotEmpty()) {
            val urls = playAddr.mapNotNull { el ->
                runCatching {
                    val o = el.jsonObject
                    val addr = str(o, "addr")
                    if (addr.isEmpty()) null else joinUrl(str(o, "m3u8FileDomain"), addr)
                }.getOrNull()
            }
            if (urls.isNotEmpty()) return Pair(urls.first(), "")
        }
        // 格式 B：decrypt_response 结果 {"data":{"sign":{"url":"..."}}}
        val signNode = pd["sign"]?.jsonObject
        val url = signNode?.let { str(it, "url") } ?: str(pd, "url")
        return if (url.startsWith("http")) Pair(url, "") else Pair(null, "解析失败: ${plain.take(300)}")
    }

    /** 拼接上游地址：addr 可能是 /path 或 //path，m3u8FileDomain 可能带尾斜杠 */
    private fun joinUrl(domain: String, addr: String): String {
        if (addr.startsWith("http")) return addr
        if (domain.isEmpty()) return addr
        return domain.trimEnd('/') + "/" + addr.trimStart('/')
    }

    private fun sign(deviceId: String, code: String, episodeNum: Int, action: String, paramsJson: String, adToken: String?): String {
        val body = """{"code":"$code","episodeNum":$episodeNum,"action":"$action","params":$paramsJson}"""
        return SecureApi.postEncrypted(Config.PATH_RESOLVE_SIGN, body, deviceId, adToken)
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

// ---------------- 首页轮播 / 评论 / 弹幕 ----------------

data class BannerItem(val id: String, val title: String, val imageUrl: String, val videoId: String)

data class CommentItem(
    val id: String, val nickname: String, val avatar: String, val content: String,
    val likeCount: Int, val replyCount: Int, val createdAt: Long, val isTop: Boolean,
)

data class DanmakuItem(val timeMs: Long, val text: String, val color: String, val fontSize: Int, val type: Int)

/** 首页轮播（banner/list，明文接口）。 */
object BannerRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun list(deviceId: String): List<BannerItem> {
        val resp = SecureApi.postEncrypted(Config.PATH_BANNER_LIST, "{}", deviceId)
        val root = json.parseToJsonElement(resp).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val arr = data["banners"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            runCatching {
                val o = el.jsonObject
                BannerItem(
                    id = str(o, "id"),
                    title = str(o, "title"),
                    imageUrl = str(o, "imageUrl"),
                    videoId = str(o, "linkId"),
                )
            }.getOrNull()
        }.filter { it.imageUrl.isNotEmpty() }
    }

    private fun str(o: kotlinx.serialization.json.JsonObject, k: String): String =
        o[k]?.jsonPrimitive?.contentOrNull ?: ""
}

/** 评论/讨论 + 弹幕。 */
object CommunityRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun str(o: kotlinx.serialization.json.JsonObject, vararg keys: String): String {
        for (k in keys) o[k]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) return it }
        return ""
    }

    fun comments(deviceId: String, videoId: String, pageIndex: Int = 1, pageSize: Int = 20): List<CommentItem> {
        val body = """{"videoId":"$videoId","pageIndex":$pageIndex,"pageSize":$pageSize}"""
        val resp = SecureApi.postEncrypted(Config.PATH_COMMENT_LIST, body, deviceId)
        val root = json.parseToJsonElement(resp).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val arr = data["comments"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            runCatching {
                val o = el.jsonObject
                CommentItem(
                    id = str(o, "id"),
                    nickname = str(o, "userNickname"),
                    avatar = str(o, "userAvatar"),
                    content = str(o, "content"),
                    likeCount = o["likeCount"]?.jsonPrimitive?.intOrNull ?: 0,
                    replyCount = o["replyCount"]?.jsonPrimitive?.intOrNull ?: 0,
                    createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                    isTop = (o["isTop"]?.jsonPrimitive?.intOrNull ?: 0) == 1,
                )
            }.getOrNull()
        }
    }

    fun danmaku(deviceId: String, videoId: String, episodeNum: Int): List<DanmakuItem> {
        val body = """{"videoId":"$videoId","episodeNum":$episodeNum}"""
        val resp = SecureApi.postEncrypted(Config.PATH_COMMENT_DANMAKU, body, deviceId)
        val root = json.parseToJsonElement(resp).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val arr = data["list"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            runCatching {
                val o = el.jsonObject
                DanmakuItem(
                    timeMs = (o["time"]?.jsonPrimitive?.longOrNull ?: 0L) * 1000L,
                    text = str(o, "text"),
                    color = str(o, "color").ifEmpty { "#FFFFFF" },
                    fontSize = o["fontSize"]?.jsonPrimitive?.intOrNull ?: 25,
                    type = o["type"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }.getOrNull()
        }.filter { it.text.isNotEmpty() }
    }
}
