package com.jcy.client.api;

import android.content.Context;
import android.util.Log;

import com.jcy.client.ApiConfig;
import com.jcy.client.crypto.Bootstrap;
import com.jcy.client.crypto.Crypto;
import com.jcy.client.crypto.Fingerprint;
import com.jcy.client.crypto.MagicInit;

import com.yy.myuko.feature.encryptionsdk.bridge.MagicSdkLib;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApiClient {
    private static final String TAG = "JcyApi";

    private final Context context;
    private final Bootstrap.Config config;
    private final String deviceId;
    private final List<String> baseUrls;
    private final byte[] deviceSecret;
    private String currentBaseUrl;

    public ApiClient(Context context, Bootstrap.Config config, String deviceId,
                     List<String> baseUrls) throws Exception {
        this.context = context.getApplicationContext();
        this.config = config;
        this.deviceId = deviceId;
        this.baseUrls = baseUrls;
        this.deviceSecret = config.deviceSecret(deviceId);
        MagicInit.ensure();
    }

    public JSONObject post(String path, JSONObject body) throws Exception {
        Exception last = null;
        for (String baseUrl : baseUrls) {
            try {
                JSONObject result = postOne(baseUrl, path, body);
                currentBaseUrl = baseUrl;
                return result;
            } catch (Exception e) {
                last = e;
                Log.w(TAG, "request failed: " + baseUrl + path + " -> " + e);
            }
        }
        throw new IllegalStateException("所有 endpoint 均失败: " + path, last);
    }

    private JSONObject postOne(String baseUrl, String path, JSONObject body) throws Exception {
        long now = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String signature = Crypto.md5Hex(now + nonce + ApiConfig.APP_ID + config.signSecret);

        byte[] plain = body.toString().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = MagicSdkLib.encryptV3(deviceSecret, plain, now, 0);
        if (encrypted == null || encrypted.length == 0) {
            throw new IllegalStateException("encryptV3 返回空");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/octet-stream");
        headers.put("Accept", "application/octet-stream");
        headers.put("X-App-Id", ApiConfig.APP_ID);
        headers.put("X-Platform", "android");
        headers.put("X-Timestamp", String.valueOf(now));
        headers.put("X-Nonce", nonce);
        headers.put("X-Signature", signature);
        try {
            headers.putAll(Fingerprint.headers(context, config, deviceId));
        } catch (Throwable e) {
            Log.w(TAG, "fingerprint failed, continue without it", e);
        }

        byte[] response = Http.postBytes(baseUrl + path, encrypted, headers);
        if (response.length == 0) {
            throw new IllegalStateException("空响应");
        }

        byte[] decoded;
        if (looksLikeJson(response)) {
            decoded = response;
        } else {
            try {
                decoded = MagicSdkLib.decryptV3(deviceSecret, response);
            } catch (Throwable v3Error) {
                decoded = MagicSdkLib.decrypt(deviceSecret, response);
            }
            if (decoded == null || decoded.length == 0) {
                throw new IllegalStateException("响应解密失败");
            }
        }

        JSONObject envelope = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
        int code = envelope.optInt("code", -1);
        if (code != 0) {
            throw new ApiException(code, envelope.optString("msg",
                    envelope.optString("message", "")));
        }
        JSONObject data = envelope.optJSONObject("data");
        return data == null ? new JSONObject() : data;
    }

    private static boolean looksLikeJson(byte[] data) {
        for (byte b : data) {
            if (b == ' ' || b == '\n' || b == '\r' || b == '\t') {
                continue;
            }
            return b == '{' || b == '[';
        }
        return false;
    }

    public String getCurrentBaseUrl() {
        return currentBaseUrl;
    }
}
