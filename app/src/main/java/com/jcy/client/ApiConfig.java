package com.jcy.client;

public final class ApiConfig {
    public static final String APP_ID = "316890437103771648";
    public static final String ADDRESSING_APP_ID = "2090640174649008128";
    public static final String PACKAGE_NAME = "com.dskiow.cjsq.p1";
    public static final String APP_VERSION = "1.0.11";
    public static final int APP_VERSION_CODE = 112;

    // 原 APK 的签名证书 SHA-256；服务端指纹里会带这个字段。
    public static final String APP_SIG_SHA256 =
            "de17140c22b492a9e38d188c675a185d9d67e1607342f03ed0a181d9f3696e74";

    public static final String PAYLOAD0_HEX = "3a881c742789416a";
    public static final String PAYLOAD1_HEX = "997ade9f1b84679a";
    public static final String PAYLOAD2_HEX = "60b97b4906521e31";
    public static final String PAYLOAD3_HEX = "859a046e3fe1b4ab";
    public static final String PAYLOAD_VERSION_HEX = "9b538b49";

    public static final String FALLBACK_HTTPS = "https://qbfbf.zshtys888.com";
    public static final String FALLBACK_HTTP = "http://1.14.219.185:7090";

    public static final String[] RESOLVER_NODES = {
            "http://43.162.222.119:8080",
            "http://101.32.65.111:8080",
            "http://43.162.221.241:8080"
    };

    private ApiConfig() {
    }
}
