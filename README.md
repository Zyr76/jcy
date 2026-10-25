# jcy — 囧次元 Kotlin 客户端

用「囧次元 1.0.11」(com.dskiow.cjsq.p1) 逆出的协议重写的极简 Kotlin 客户端：主页展示影视条目，点击进入播放。

## 协议（逆向结论）

```
Addressing.resolve   GET  {bootstrap}:8080/api/v1/resolve?app_id=2090640174649008128
                     -> endpoints.polyvision = 业务网关（https://qbfbf.zshtys888.com 等）

Bootstrap            GET  {gateway}/api/v1/app/client/auth/bootstrap.jpg?appId&sv&_t
                     JPEG 尾部隐写: MAGIC(89 50 43 47) len(4BE) IV(16) ciphertext(len) HMAC(32)
                     候选密钥 = nativeBootstrapDeriveCandidateKeys(payload0..3, appId, nowSec) 192B(3×64)
                     校验 HMAC-SHA256(key[32:64], iv||ct)；AES-256-CBC(key[0:32], iv) 解密 -> BootstrapConfig
                     BootstrapConfig.masterSecret -> EncryptionSDK

Negotiate            POST {gateway}/api/v1/app/client/auth/negotiate  {"deviceId","platform"}
                     响应为 V3 加密的 DeviceSecretInfo{deviceSecret,keyVersion,...}

业务请求             POST {gateway}/api/v1/app/client/{...}?app_id=316890437103771648
                     Header: X-Platform / X-App-Id / X-Key-Version / X-Device-Id
                     Body:  application/octet-stream，内容 = native encryptV3(deviceSecret, json, nowMs, nodeCount)
                     响应带 X-Encrypted 头或首字节 3 -> native decryptV3 解密
```

接口：`/home/recommend`、`/video/modules`、`/video/detail`、`/video/search`

## native

`libmagic_cipher.so`（官方，三 ABI）通过同名字符串签名调用：
`com.yy.myuko.feature.encryptionsdk.bridge.MagicSdkLib` / `MagicFingerprintLib`。
`MagicSdkLib.init` 注册算法链（XOR/RC4/Base64/TEA/ChaCha20/Blowfish），
`encryptV3 / decryptV3` 完成 V3 信封加解密。

## 构建

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ANDROID_HOME=~/android/sdk ./gradlew assembleDebug
```

产物 `app/build/outputs/apk/debug/app-debug.apk`。
