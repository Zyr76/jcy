package com.jcy.client.api;

import com.jcy.client.model.VideoItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class HomeRepository {
    private HomeRepository() {
    }

    public static List<VideoItem> load(ApiClient api) throws Exception {
        List<VideoItem> items = new ArrayList<>();
        try {
            JSONObject body = new JSONObject();
            body.put("categoryId", JSONObject.NULL);
            body.put("tags", new JSONArray());
            body.put("pageIndex", 0);
            body.put("pageSize", 20);
            body.put("videoId", JSONObject.NULL);

            JSONObject data = api.post("/api/v1/app/client/home/recommend", body);
            JSONObject like = data.optJSONObject("guessYouLike");
            JSONArray videos = like != null ? like.optJSONArray("videos") : data.optJSONArray("videos");
            append(items, videos);
        } catch (Exception ignored) {
        }

        if (items.isEmpty()) {
            JSONObject body = new JSONObject();
            body.put("categoryId", JSONObject.NULL);
            body.put("pageIndex", 0);
            body.put("pageSize", 20);
            JSONObject data = api.post("/api/v1/app/client/video/modules", body);
            JSONArray modules = data.optJSONArray("list");
            if (modules != null) {
                for (int i = 0; i < modules.length(); i++) {
                    JSONObject module = modules.optJSONObject(i);
                    if (module != null) {
                        append(items, module.optJSONArray("items"));
                    }
                }
            }
        }

        if (items.isEmpty()) {
            throw new IllegalStateException("首页接口没有返回影视数据");
        }
        return items;
    }

    private static void append(List<VideoItem> out, JSONArray array) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject video = array.optJSONObject(i);
            if (video == null) {
                continue;
            }
            String id = video.optString("id", "");
            if (id.isEmpty()) {
                continue;
            }
            String cover = video.optString("coverUrl", video.optString("posterUrl", ""));
            String subTitle = video.optString("subTitle", "");
            out.add(new VideoItem(
                    id,
                    video.optString("title", "未命名"),
                    cover,
                    subTitle,
                    video.optInt("currentEpisodes", 0),
                    video.optInt("totalEpisodes", 0),
                    video.optDouble("score", 0)
            ));
        }
    }
}
