# 囧次元客户端（Flutter）

目标：按 `flutter 逆向靶场.apk`（com.tingfeng.tool）的协议重建客户端。逻辑与 UI 全部在新工程里实现，只复用原 APK 的原生核。

## 当前阶段：协议探针

已内置原 APK 的原生库（`android/app/src/main/jniLibs/arm64-v8a/`）：

- `libloader.so` —— DNS/HTTP/OpenSSL/Lua，导出 `call` / `init` / `reload`
- `libcore.so` —— 业务加密（protobuf），导出 `call` / `init`
- `liblua-core.so` —— Lua 运行时

探针页面可逐个触发并显示明文请求 / 密文 / 解密后的响应：

- `get_app_info`、`get_host_config`、`get_registries`（走 libloader，AES-CBC 密钥 `kFGTbLlOzFHQCIKp` / `F3q22XoM8l6T2Ydc`）
- `core:get_app_info`（走 libcore，密钥 `qPwClBj7j7ZQraSm` / `p3JdVQl3q7WQJIgG`）
- `init`（明文 JSON：app_id / device_id / code_version / app_version / files_path / tcp）

> applicationId 固定为 `com.tingfeng.tool`，因为原生核与签名/包名强绑定；安装前需卸载原 App。

## 构建

```bash
flutter pub get
flutter build apk --debug --target-platform android-arm64
```

CI 见 `.github/workflows/android.yml`。
