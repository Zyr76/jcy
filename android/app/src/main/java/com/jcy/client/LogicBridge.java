package com.jcy.client;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.jcy.client.api.HomeRepository;
import com.jcy.client.api.PlaybackRepository;
import com.jcy.client.model.PlayInfo;
import com.jcy.client.model.VideoItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public final class LogicBridge implements MethodChannel.MethodCallHandler {
    static final String CHANNEL = "com.jcy.client/logic";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    LogicBridge(Context context) {
        this.context = context;
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        switch (call.method) {
            case "home":
                executor.execute(() -> {
                    try {
                        Session session = Session.init(context);
                        List<VideoItem> items = HomeRepository.load(session.api);
                        List<Map<String, Object>> payload = new ArrayList<>(items.size());
                        for (VideoItem item : items) {
                            payload.add(toMap(item));
                        }
                        success(result, payload);
                    } catch (Exception error) {
                        failure(result, error);
                    }
                });
                break;
            case "resolve": {
                String videoId = call.argument("videoId");
                if (videoId == null || videoId.isEmpty()) {
                    result.error("bad_args", "videoId is required", null);
                    return;
                }
                executor.execute(() -> {
                    try {
                        Session session = Session.init(context);
                        PlayInfo info = PlaybackRepository.resolve(context, session.api, videoId);
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("title", info.title);
                        payload.put("url", info.playUrl);
                        payload.put("localPlaylist", info.localPlaylist);
                        success(result, payload);
                    } catch (Exception error) {
                        failure(result, error);
                    }
                });
                break;
            }
            default:
                result.notImplemented();
        }
    }

    private static Map<String, Object> toMap(VideoItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", item.id);
        map.put("title", item.title);
        map.put("cover", item.coverUrl);
        map.put("subTitle", item.subTitle);
        map.put("currentEpisodes", item.currentEpisodes);
        map.put("totalEpisodes", item.totalEpisodes);
        map.put("score", item.score);
        return map;
    }

    private void success(MethodChannel.Result result, Object value) {
        mainHandler.post(() -> result.success(value));
    }

    private void failure(MethodChannel.Result result, Throwable error) {
        StringBuilder message = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (message.length() > 0) {
                message.append(" <- ");
            }
            message.append(current.getClass().getSimpleName());
            if (current.getMessage() != null) {
                message.append(": ").append(current.getMessage());
            }
            current = current.getCause();
        }
        mainHandler.post(() -> result.error("logic", message.toString(), null));
    }
}
