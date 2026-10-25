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
        return VideoDetail(
            id = str(data, "id", "videoId"),
            name = str(data, "name", "title"),
            cover = str(data, "coverUrl", "cover"),
            description = str(data, "description", "desc"),
            sources = sources,
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
