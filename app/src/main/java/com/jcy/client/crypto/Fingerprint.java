package com.jcy.client.crypto;

import android.content.Context;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;

import com.jcy.client.ApiConfig;

import com.yy.myuko.feature.encryptionsdk.bridge.MagicFingerprintLib;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class Fingerprint {
    private Fingerprint() {
    }

    public static Map<String, String> headers(Context context, Bootstrap.Config config,
                                               String deviceId) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Device-Id", deviceId);
        if (!config.hasFingerprint()) {
            return headers;
        }

        byte[] secret = config.fpHmacSecret();
        byte[] table = config.mbase64TableBytes();
        if (secret.length != 32) {
            throw new IllegalStateException("fp hmacSecret 长度异常: " + secret.length);
        }
        if (table.length != 64) {
            throw new IllegalStateException("mbase64Table 长度异常: " + table.length);
        }

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        JSONObject json = new JSONObject();
        json.put("fp", deviceId);
        json.put("dn", trim(Build.BRAND + " " + Build.MODEL));
        json.put("dm", trim(Build.MANUFACTURER + " " + Build.MODEL));
        json.put("os", isTelevision(context) ? "AndroidTV" : "Android");
        json.put("osv", blankTo(Build.VERSION.RELEASE, "Unknown"));
        json.put("av", ApiConfig.APP_VERSION);
        json.put("avc", ApiConfig.APP_VERSION_CODE);
        json.put("pkg", ApiConfig.PACKAGE_NAME);
        json.put("asig", ApiConfig.APP_SIG_SHA256);
        json.put("hv", ApiConfig.APP_VERSION);
        json.put("pt", "android");
        json.put("dt", context.getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 2 : 1);
        json.put("sw", metrics.widthPixels);
        json.put("sh", metrics.heightPixels);
        json.put("lang", Locale.getDefault().toLanguageTag());
        json.put("tz", TimeZone.getDefault().getID());
        json.put("net", networkType(context));
        json.put("ts", System.currentTimeMillis());
        json.put("bi", biEncode(ApiConfig.APP_VERSION, ApiConfig.APP_VERSION_CODE, false));

        byte[] plain = json.toString().getBytes(StandardCharsets.UTF_8);
        byte[] maes = MagicFingerprintLib.maesProcess(
                java.util.Arrays.copyOfRange(secret, 0, 16),
                java.util.Arrays.copyOfRange(secret, 16, 32),
                plain,
                true
        );
        if (maes == null) {
            throw new IllegalStateException("maesProcess 返回 null");
        }
        byte[] encoded = MagicFingerprintLib.mbase64Process(table, maes, true);
        if (encoded == null) {
            throw new IllegalStateException("mbase64Process 返回 null");
        }
        String headerValue = new String(encoded, StandardCharsets.UTF_8);
        String signature = Crypto.bytesToHex(
                Crypto.hmacSha256(secret, headerValue.getBytes(StandardCharsets.UTF_8)));

        headers.put("X-Device-Fingerprint", headerValue);
        headers.put("X-FP-Sign", signature);
        headers.put("X-FP-Algo", String.valueOf(config.fpAlgoVersion));
        return headers;
    }

    private static String biEncode(String appVersion, long versionCode, boolean specialEdition) {
        int flags = (specialEdition ? 8 : 0) | 16;
        StringBuilder out = new StringBuilder();
        for (int i = 7; i >= 4; i--) {
            out.append(((1 << i) & flags) == 0 ? '\u200B' : '\u2060');
        }
        out.append(appVersion).append('-').append(versionCode);
        for (int i = 3; i >= 0; i--) {
            out.append(((1 << i) & flags) == 0 ? '\u200B' : '\u2060');
        }
        return out.toString();
    }

    private static boolean isTelevision(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private static String trim(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? "Unknown" : trimmed;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String networkType(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return "other";
            }
            Network active = cm.getActiveNetwork();
            NetworkCapabilities capabilities = active == null ? null : cm.getNetworkCapabilities(active);
            if (capabilities == null) {
                return "other";
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return "wifi";
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return "ethernet";
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                TelephonyManager telephony = (TelephonyManager) context.getSystemService(
                        Context.TELEPHONY_SERVICE);
                int type = telephony == null ? 0 : telephony.getDataNetworkType();
                if (type == TelephonyManager.NETWORK_TYPE_NR) {
                    return "5g";
                }
                if (type == TelephonyManager.NETWORK_TYPE_LTE) {
                    return "4g";
                }
            }
        } catch (Throwable ignored) {
        }
        return "other";
    }
}
