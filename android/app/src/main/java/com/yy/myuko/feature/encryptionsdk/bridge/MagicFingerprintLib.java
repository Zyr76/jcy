package com.yy.myuko.feature.encryptionsdk.bridge;

public final class MagicFingerprintLib {
    public static final MagicFingerprintLib INSTANCE = new MagicFingerprintLib();

    static {
        System.loadLibrary("magic_cipher");
    }

    private MagicFingerprintLib() {
    }

    public static native byte[] maesProcess(byte[] key1, byte[] key2, byte[] data, boolean encrypt);

    public static native byte[] mbase64Process(byte[] table, byte[] data, boolean encode);

    public static native int nativeBootstrapAssembleVersion(byte[] payloadVersion);

    public static native byte[] nativeBootstrapDeriveCandidateKeys(byte[] payload0, byte[] payload1,
                                                                   byte[] payload2, byte[] payload3,
                                                                   String appId, long timeSeconds);
}
