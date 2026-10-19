package com.jcy.client.api;

import android.content.Context;

import com.jcy.client.model.PlayInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class PlaybackRepository {
    private PlaybackRepository() {
    }

    public static PlayInfo resolve(Context context, ApiClient api, String videoId) throws Exception {
        JSONObject detailBody = new JSONObject();
        detailBody.put("videoId", videoId);
        JSONObject detailData = api.post("/api/v1/app/client/video/detail", detailBody);
        JSONObject video = detailData.optJSONObject("video");
        if (video == null) {
            video = detailData;
        }
        String title = video.optString("title", "未命名");

        String code = "";
        JSONArray players = video.optJSONArray("player");
        if (players != null && players.length() > 0) {
            JSONObject first = players.optJSONObject(0);
            if (first != null) {
                code = first.optString("playerCode", "");
            }
        }
        if (code.isEmpty()) {
            code = video.optString("playerCode", "");
        }
        if (code.isEmpty()) {
            throw new IllegalStateException("视频详情没有 playerCode");
        }

        JSONObject qualitiesBody = new JSONObject();
        qualitiesBody.put("videoId", videoId);
        qualitiesBody.put("code", code);
        qualitiesBody.put("episodeNum", 1);
        qualitiesBody.put("caps", caps());
        JSONObject qualitiesData = api.post("/api/v1/app/client/resolve/qualities", qualitiesBody);

        String quality = "";
        JSONArray qualities = qualitiesData.optJSONArray("qualities");
        if (qualities != null && qualities.length() > 0) {
            JSONObject selected = qualities.optJSONObject(0);
            for (int i = 0; i < qualities.length(); i++) {
                JSONObject candidate = qualities.optJSONObject(i);
                if (candidate != null && !candidate.optBoolean("isVipOnly", false)) {
                    selected = candidate;
                    break;
                }
            }
            if (selected != null) {
                quality = selected.optString("quality", "");
            }
        }
        String exchangeToken = qualitiesData.optString("exchangeToken", "");

        JSONObject exchangeBody = new JSONObject();
        exchangeBody.put("videoId", videoId);
        exchangeBody.put("code", code);
        exchangeBody.put("episodeNum", 1);
        exchangeBody.put("quality", quality);
        exchangeBody.put("exchangeToken", exchangeToken);
        exchangeBody.put("adToken", JSONObject.NULL);
        exchangeBody.put("caps", caps());
        JSONObject exchangeData = api.post("/api/v1/app/client/resolve/exchange", exchangeBody);

        String playUrl = exchangeData.optString("playUrl", "");
        if (playUrl.isEmpty()) {
            playUrl = exchangeData.optString("originalUrl", "");
        }
        if (!playUrl.isEmpty()) {
            return new PlayInfo(title, absoluteUrl(api, playUrl), false);
        }

        JSONObject playlist = exchangeData.optJSONObject("playlist");
        if (playlist != null) {
            File file = buildM3u8(context, api, playlist);
            return new PlayInfo(title, file.getAbsolutePath(), true);
        }

        String lua = exchangeData.optString("luaScript",
                exchangeData.optString("clientScript",
                        exchangeData.optString("fallbackLuaScript", "")));
        if (!lua.isEmpty()) {
            throw new IllegalStateException("该集返回 Lua 解析脚本，当前原型仅支持直链/HLS");
        }
        throw new IllegalStateException("resolve/exchange 未返回 playUrl 或 playlist");
    }

    private static JSONArray caps() {
        JSONArray caps = new JSONArray();
        caps.put("v2ts");
        return caps;
    }

    private static File buildM3u8(Context context, ApiClient api, JSONObject playlist) throws Exception {
        JSONArray segments = playlist.optJSONArray("segments");
        if (segments == null || segments.length() == 0) {
            throw new IllegalStateException("playlist 没有 segments");
        }
        double maxDuration = 1;
        for (int i = 0; i < segments.length(); i++) {
            JSONObject segment = segments.optJSONObject(i);
            if (segment != null) {
                maxDuration = Math.max(maxDuration, segment.optDouble("duration", 1));
            }
        }
        int targetDuration = Math.max(1, (int) Math.ceil(maxDuration));

        StringBuilder out = new StringBuilder();
        out.append("#EXTM3U\n");
        out.append("#EXT-X-VERSION:3\n");
        out.append("#EXT-X-PLAYLIST-TYPE:VOD\n");
        out.append("#EXT-X-TARGETDURATION:").append(targetDuration).append('\n');
        out.append("#EXT-X-MEDIA-SEQUENCE:0\n");

        JSONObject key = playlist.optJSONObject("key");
        if (key != null) {
            String method = key.optString("method", "");
            String uri = key.optString("uri", "");
            if (!method.isEmpty() && !"NONE".equalsIgnoreCase(method) && !uri.isEmpty()) {
                out.append("#EXT-X-KEY:METHOD=").append(method)
                        .append(",URI=\"").append(absoluteUrl(api, uri)).append('"');
                String iv = key.optString("iv", "");
                if (!iv.isEmpty()) {
                    out.append(",IV=").append(iv.startsWith("0x") ? iv : "0x" + iv);
                }
                out.append('\n');
            }
        }

        for (int i = 0; i < segments.length(); i++) {
            JSONObject segment = segments.optJSONObject(i);
            if (segment == null) {
                continue;
            }
            String url = segment.optString("url", "");
            if (url.isEmpty()) {
                continue;
            }
            out.append("#EXTINF:").append(segment.optDouble("duration", 0)).append(",\n");
            out.append(absoluteUrl(api, url)).append('\n');
        }
        out.append("#EXT-X-ENDLIST\n");

        File file = new File(context.getCacheDir(), "jcy_playlist.m3u8");
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write(out.toString().getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static String absoluteUrl(ApiClient api, String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        String base = api.getCurrentBaseUrl();
        if (base == null) {
            base = "";
        }
        if (url.startsWith("/")) {
            return base + url;
        }
        return base + "/" + url;
    }
}
