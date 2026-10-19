package com.jcy.client.crypto;

import com.yy.myuko.feature.encryptionsdk.bridge.MagicSdkLib;

import java.nio.charset.StandardCharsets;

public final class MagicInit {
    private static boolean initialized;

    private MagicInit() {
    }

    public static synchronized void ensure() {
        if (initialized) {
            return;
        }
        // 算法 ID = SHA-256("EncryptionSDK.<name>.<keyLength>") 各字节异或。
        // 原始注册顺序的 ID/映射/密钥长度如下，按无符号 ID 升序传给 native。
        MagicSdkLib.init(
                new byte[]{0x19, 0x3b, 0x75, (byte) 0x82, (byte) 0x9a, (byte) 0xe4},
                new byte[]{2, 5, 6, 3, 4, 1},
                new short[]{16, 44, 16, 0, 16, 8},
                "wire-code-mask".getBytes(StandardCharsets.UTF_8),
                "payload-integrity".getBytes(StandardCharsets.UTF_8)
        );
        initialized = true;
    }
}
