package com.yy.myuko.feature.encryptionsdk.bridge;

public final class MagicSdkLib {
    public static final MagicSdkLib INSTANCE = new MagicSdkLib();

    static {
        System.loadLibrary("magic_cipher");
    }

    private MagicSdkLib() {
    }

    public static native byte[] decrypt(byte[] secret, byte[] payload);

    public static native byte[] decryptV3(byte[] secret, byte[] payload);

    public static native byte[] deriveCode(byte[] secret, long time, int nodeCount);

    public static native byte[] deriveUserCode(byte[] secret, byte[] userId, long time, int nodeCount);

    public static native byte[] deriveWireMask(byte[] secret, long slot);

    public static native byte[] encryptV3(byte[] secret, byte[] plaintext, long time, int nodeCount);

    public static native byte[] encryptV3WithCode(byte[] secret, byte[] plaintext, byte[] code, long time);

    public static native byte[] hmacSha256(byte[] key, byte[] data);

    public static native void init(byte[] algorithmIds, byte[] mappedIds, short[] keyLengths,
                                   byte[] wireMaskDomain, byte[] integrityDomain);

    public static native boolean isInGracePeriod(long time);

    public static native byte[] obfuscateCode(byte[] code, byte[] mask);

    public static native byte[] secureRandom(int size);

    public static native byte[] sha256(byte[] data);

    public static native byte[] signCode(byte[] code, byte[] secret, long time);

    public static native long slotFromTime(long time);

    public static native byte[] verifySignedCode(byte[] code, byte[] secret, long time, long maxAgeSeconds);
}
