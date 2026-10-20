package com.jcy.client.api;

import com.jcy.client.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class EndpointResolver {
    private EndpointResolver() {
    }

    public static List<String> resolve() {
        List<String> urls = new ArrayList<>();
        for (String node : ApiConfig.RESOLVER_NODES) {
            try {
                String url = node + "/api/v1/resolve?app_id=" + ApiConfig.ADDRESSING_APP_ID;
                byte[] body = Http.getBytes(url, null);
                JSONObject root = new JSONObject(new String(body, StandardCharsets.UTF_8));
                JSONObject data = root.optJSONObject("data");
                if (data == null) {
                    continue;
                }
                JSONObject endpoints = data.optJSONObject("endpoints");
                if (endpoints == null) {
                    continue;
                }
                JSONArray polyvision = endpoints.optJSONArray("polyvision");
                if (polyvision == null) {
                    continue;
                }
                for (int i = 0; i < polyvision.length(); i++) {
                    JSONObject endpoint = polyvision.optJSONObject(i);
                    if (endpoint == null) {
                        continue;
                    }
                    String protocol = endpoint.optString("protocol", "https");
                    String host = endpoint.optString("host", "");
                    if (host.isEmpty()) {
                        host = endpoint.optString("ip", "");
                    }
                    if (host.isEmpty()) {
                        continue;
                    }
                    int port = endpoint.optInt("port", "https".equals(protocol) ? 443 : 80);
                    boolean defaultPort = ("https".equals(protocol) && port == 443)
                            || ("http".equals(protocol) && port == 80);
                    String base = protocol + "://" + host + (defaultPort ? "" : ":" + port);
                    if (!urls.contains(base)) {
                        urls.add(base);
                    }
                }
            } catch (Exception ignored) {
                // try next bootstrap node
            }
        }
        if (!urls.contains(ApiConfig.FALLBACK_HTTPS)) {
            urls.add(ApiConfig.FALLBACK_HTTPS);
        }
        if (!urls.contains(ApiConfig.FALLBACK_HTTP)) {
            urls.add(ApiConfig.FALLBACK_HTTP);
        }
        return urls;
    }
}
