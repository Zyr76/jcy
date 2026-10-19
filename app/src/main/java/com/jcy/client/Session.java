package com.jcy.client;

import android.content.Context;

import com.jcy.client.api.ApiClient;
import com.jcy.client.api.EndpointResolver;
import com.jcy.client.crypto.Bootstrap;
import com.jcy.client.crypto.MagicInit;

import java.util.List;
import java.util.UUID;

public final class Session {
    private static Session instance;

    public final ApiClient api;
    public final Bootstrap.Config config;
    public final List<String> baseUrls;
    public final String deviceId;

    private Session(ApiClient api, Bootstrap.Config config, List<String> baseUrls, String deviceId) {
        this.api = api;
        this.config = config;
        this.baseUrls = baseUrls;
        this.deviceId = deviceId;
    }

    public static synchronized Session get() {
        return instance;
    }

    public static synchronized Session init(Context context) throws Exception {
        if (instance != null) {
            return instance;
        }
        Context app = context.getApplicationContext();
        MagicInit.ensure();
        List<String> baseUrls = EndpointResolver.resolve();
        Bootstrap.Config config = Bootstrap.fetch(baseUrls);
        String deviceId = deviceId(app);
        ApiClient api = new ApiClient(app, config, deviceId, baseUrls);
        instance = new Session(api, config, baseUrls, deviceId);
        return instance;
    }

    private static String deviceId(Context context) {
        String key = "device_id";
        String value = context.getSharedPreferences("jcy", Context.MODE_PRIVATE)
                .getString(key, "");
        if (value != null && !value.isEmpty()) {
            return value;
        }
        value = (UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", ""));
        context.getSharedPreferences("jcy", Context.MODE_PRIVATE)
                .edit().putString(key, value).apply();
        return value;
    }
}
