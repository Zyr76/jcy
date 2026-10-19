package com.jcy.client.crypto;

import com.jcy.client.ApiConfig;

import com.yy.myuko.feature.encryptionsdk.bridge.MagicFingerprintLib;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Bootstrap {
    private Bootstrap() {
    }

    public static Config fetch(List<String> baseUrls) throws Exception {
        Exception last = null;
        for (String base : baseUrls) {
            try {
                return fetchOne(base);
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IllegalStateException("bootstrap 全部失败: " + describe(last), last);
    }

    private static Config fetchOne(String baseUrl) throws Exception {
        int sv = 0;
        try {
            sv = MagicFingerprintLib.nativeBootstrapAssembleVersion(
                    Crypto.hexToBytes(ApiConfig.PAYLOAD_VERSION_HEX));
        } catch (Throwable ignored) {
        }

        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            int version = attempt == 0 ? sv : 0;
            try {
                String url = baseUrl + "/api/v1/app/client/auth/bootstrap.jpg?appId="
                        + ApiConfig.APP_ID + "&sv=" + version + "&_t=" + System.currentTimeMillis();
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("X-App-Id", ApiConfig.APP_ID);
                headers.put("X-Platform", "android");
                byte[] jpeg = HttpHolder.get(url, headers);
                return decode(jpeg);
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IllegalStateException("bootstrap 解码失败: " + baseUrl + " -> " + describe(last), last);
    }

    private static Config decode(byte[] jpeg) throws Exception {
        Stego stego = parseStego(jpeg);
        long nowSeconds = System.currentTimeMillis() / 1000L;
        long[] timeOffsets = {0L, -30L * 86400L, 30L * 86400L};
        for (long offset : timeOffsets) {
            byte[] keyBlob = MagicFingerprintLib.nativeBootstrapDeriveCandidateKeys(
                    Crypto.hexToBytes(ApiConfig.PAYLOAD0_HEX),
                    Crypto.hexToBytes(ApiConfig.PAYLOAD1_HEX),
                    Crypto.hexToBytes(ApiConfig.PAYLOAD2_HEX),
                    Crypto.hexToBytes(ApiConfig.PAYLOAD3_HEX),
                    ApiConfig.APP_ID,
                    nowSeconds + offset
            );
            if (keyBlob == null || keyBlob.length != 192) {
                continue;
            }

            for (int i = 0; i < 3; i++) {
                byte[] key = Arrays.copyOfRange(keyBlob, i * 64, (i + 1) * 64);
                byte[] mac = Crypto.hmacSha256(
                        Arrays.copyOfRange(key, 32, 64),
                        Crypto.concat(stego.iv, stego.cipherText)
                );
                if (!Crypto.constantTimeEquals(mac, stego.hmac)) {
                    continue;
                }
                byte[] plain = Crypto.aes256CbcDecrypt(
                        Arrays.copyOfRange(key, 0, 32), stego.iv, stego.cipherText);
                JSONObject json = new JSONObject(new String(plain, StandardCharsets.UTF_8));
                return Config.parse(json);
            }
        }
        throw new SecurityException("bootstrap HMAC 校验失败，候选密钥均不匹配");
    }

    private static Stego parseStego(byte[] image) {
        int eoi = -1;
        for (int i = image.length - 2; i >= 0; i--) {
            if ((image[i] & 0xff) == 0xff && (image[i + 1] & 0xff) == 0xd9) {
                eoi = i;
                break;
            }
        }
        if (eoi < 0) {
            throw new IllegalArgumentException("不是有效 JPEG（未找到 EOI）");
        }
        int offset = eoi + 2;
        int tailLength = image.length - offset;
        if (tailLength < 56) {
            throw new IllegalArgumentException("JPEG 尾部没有 bootstrap 数据: " + tailLength);
        }
        if ((image[offset] & 0xff) != 0x89
                || image[offset + 1] != 'P'
                || image[offset + 2] != 'C'
                || image[offset + 3] != 'G') {
            throw new IllegalArgumentException("隐写 magic 不匹配");
        }
        int payloadLength = ((image[offset + 4] & 0xff) << 24)
                | ((image[offset + 5] & 0xff) << 16)
                | ((image[offset + 6] & 0xff) << 8)
                | (image[offset + 7] & 0xff);
        if (payloadLength <= 0 || tailLength < payloadLength + 56) {
            throw new IllegalArgumentException("bootstrap 载荷不完整: " + payloadLength);
        }
        byte[] iv = Arrays.copyOfRange(image, offset + 8, offset + 24);
        byte[] cipher = Arrays.copyOfRange(image, offset + 24, offset + 24 + payloadLength);
        byte[] hmac = Arrays.copyOfRange(image, offset + 24 + payloadLength,
                offset + 24 + payloadLength + 32);
        return new Stego(iv, cipher, hmac);
    }

    public static final class Stego {
        final byte[] iv;
        final byte[] cipherText;
        final byte[] hmac;

        Stego(byte[] iv, byte[] cipherText, byte[] hmac) {
            this.iv = iv;
            this.cipherText = cipherText;
            this.hmac = hmac;
        }
    }

    public static final class Config {
        public final String masterSecret;
        public final String signSecret;
        public final String mbase64Table;
        public final int fpAlgoVersion;
        public final String fpHmacSecretHex;
        public final long serverTime;
        public final int keyVersion;
        public final long configVersion;

        Config(String masterSecret, String signSecret, String mbase64Table,
               int fpAlgoVersion, String fpHmacSecretHex,
               long serverTime, int keyVersion, long configVersion) {
            this.masterSecret = masterSecret;
            this.signSecret = signSecret;
            this.mbase64Table = mbase64Table;
            this.fpAlgoVersion = fpAlgoVersion;
            this.fpHmacSecretHex = fpHmacSecretHex;
            this.serverTime = serverTime;
            this.keyVersion = keyVersion;
            this.configVersion = configVersion;
        }

        static Config parse(JSONObject json) {
            String masterSecret = string(json, "masterSecret", "master_secret");
            String signSecret = string(json, "signSecret", "sign_secret");
            if (masterSecret.isEmpty() || signSecret.isEmpty()) {
                throw new IllegalStateException("bootstrap 缺少 masterSecret/signSecret");
            }

            String table = "";
            JSONObject tableObject = object(json, "mbase64Table", "mbase64_table");
            if (tableObject != null) {
                table = string(tableObject, "tableStr", "table_str");
            }
            if (table.isEmpty()) {
                table = string(json, "mbase64Table", "mbase64_table");
            }

            int fpVersion = 0;
            String fpHmacSecret = "";
            JSONObject fpAlgo = object(json, "fpAlgo", "fp_algo");
            if (fpAlgo != null) {
                fpVersion = fpAlgo.optInt("version", 0);
                fpHmacSecret = string(fpAlgo, "hmacSecret", "hmac_secret");
            }

            return new Config(
                    masterSecret,
                    signSecret,
                    table,
                    fpVersion,
                    fpHmacSecret,
                    json.optLong("serverTime", json.optLong("server_time", 0L)),
                    json.optInt("keyVersion", json.optInt("key_version", 0)),
                    json.optLong("configVersion", json.optLong("config_version", 0L))
            );
        }

        public boolean hasFingerprint() {
            return !mbase64Table.isEmpty() && !fpHmacSecretHex.isEmpty();
        }

        public byte[] deviceSecret(String deviceId) throws Exception {
            byte[] hmac = Crypto.hmacSha256(
                    masterSecret.getBytes(StandardCharsets.UTF_8),
                    ("device:" + deviceId).getBytes(StandardCharsets.UTF_8)
            );
            return Crypto.bytesToHex(hmac).getBytes(StandardCharsets.UTF_8);
        }

        public byte[] fpHmacSecret() {
            return Crypto.hexToBytes(fpHmacSecretHex);
        }

        public byte[] mbase64TableBytes() {
            return mbase64Table.getBytes(StandardCharsets.UTF_8);
        }

        private static JSONObject object(JSONObject json, String... names) {
            for (String name : names) {
                JSONObject value = json.optJSONObject(name);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        private static String string(JSONObject json, String... names) {
            for (String name : names) {
                if (json.has(name) && !json.isNull(name)) {
                    String value = json.optString(name, "");
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }
            return "";
        }
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        StringBuilder out = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (out.length() > 0) {
                out.append(" <- ");
            }
            out.append(current.getClass().getSimpleName()).append(": ")
                    .append(current.getMessage());
            current = current.getCause();
        }
        return out.toString();
    }

    private static final class HttpHolder {
        static byte[] get(String url, Map<String, String> headers) throws Exception {
            return com.jcy.client.api.Http.getBytes(url, headers);
        }
    }
}
