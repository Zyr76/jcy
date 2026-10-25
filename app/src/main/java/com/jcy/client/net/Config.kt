package com.jcy.client.net

/** 从 1.0.11 APK 的 MyukoBuildConfigAndroid 逆出的常量。 */
object Config {
    // 地址分发（addressing）appId
    const val ADDRESSING_APP_ID = "2090640174649008128"
    // 业务 API appId
    const val API_APP_ID = "316890437103771648"
    // 热更新 appId（未使用）
    const val HOTPLUG_APP_ID = "2090641280894767104"

    // bootstrap.png 载荷（8 字节 hex）与版本载荷
    const val PAYLOAD0_HEX = "3a881c742789416a"
    const val PAYLOAD1_HEX = "997ade9f1b84679a"
    const val PAYLOAD2_HEX = "60b97b4906521e31"
    const val PAYLOAD3_HEX = "859a046e3fe1b4ab"
    const val PAYLOAD_V_HEX = "9b538b49"

    // 地址分发 bootstrap 节点（HTTP 明文可用）
    val ADDRESSING_BOOTSTRAP_HTTP = listOf(
        "http://43.162.222.119:8080",
        "http://101.32.65.111:8080",
        "http://43.162.221.241:8080",
    )

    // nativeSdkInit 参数（算法链）：XOR(8) RC4(16) Base64(0) TEA(16) ChaCha20(44) Blowfish(16)
    // 算法 id = xor(sha256("EncryptionSDK." + name + "." + keyLength)) 的低字节（官方 RegistryKt.computeFingerprint）
    val ALGO_IDS = byteArrayOf(0xe4.toByte(), 0x19, 0x82.toByte(), 0x9a.toByte(), 0x3b, 0x75)
    val MC_ALGO_IDS = byteArrayOf(1, 2, 3, 4, 5, 6)
    val KEY_LENGTHS = shortArrayOf(8, 16, 0, 16, 44, 16)
    // Vault 15/16 解密结果
    val WIRE_CODE_MASK = "wire-code-mask".toByteArray()
    val PAYLOAD_INTEGRITY = "payload-integrity".toByteArray()

    const val PATH_RESOLVE = "/api/v1/resolve"
    const val PATH_BOOTSTRAP_PNG = "/api/v1/app/client/auth/bootstrap.jpg"
    const val PATH_NEGOTIATE = "/api/v1/app/client/auth/negotiate"
    const val PATH_HOME_RECOMMEND = "/api/v1/app/client/home/recommend"
    const val PATH_VIDEO_MODULES = "/api/v1/app/client/video/modules"
    const val PATH_VIDEO_DETAIL = "/api/v1/app/client/video/detail"
    const val PATH_VIDEO_SEARCH = "/api/v1/app/client/video/search"
    const val PATH_RESOLVE_EPISODES = "/api/v1/app/client/resolve/episodes"
    const val PATH_RESOLVE_QUALITIES = "/api/v1/app/client/resolve/qualities"
    const val PATH_RESOLVE_SIGN = "/api/v1/app/client/resolve/sign"
    const val PATH_RESOLVE_EXCHANGE = "/api/v1/app/client/resolve/exchange"
    const val PATH_AD_GET_CONFIG = "/api/v1/app/client/ad/getConfig"
    const val PATH_AD_CLAIM_REWARD = "/api/v1/app/client/ad/claimReward"
    const val PATH_BANNER_LIST = "/api/v1/app/client/banner/list"
    const val PATH_COMMENT_LIST = "/api/v1/app/client/comment/list"
    const val PATH_COMMENT_DANMAKU = "/api/v1/app/client/comment/danmaku"
    const val PATH_REVIEW_LIST = "/api/v1/app/client/review/list"
    const val PATH_REVIEW_SUMMARY = "/api/v1/app/client/review/summary"
    const val PATH_AUTH_ENCRYPT_KEY = "/api/v1/app/client/auth/encryptKey"
    const val PATH_AUTH_LOGIN = "/api/v1/app/client/auth/login"
    const val PATH_AUTH_REGISTER = "/api/v1/app/client/auth/register"

    const val PLATFORM = "android"
}
