# 囧次元极简客户端

这是一个基于逆向分析结果的 Android 最小客户端，只做两件事：

1. 展示首页影视列表；
2. 点击后解析并播放第一集。

## 构建

```bash
./gradlew assembleDebug
```

或者推到 GitHub，使用 `.github/workflows/android.yml` 自动构建，产物在 Actions 的 `jcy-client-debug` artifact 中。

## 已实现

- 通过 `http://43.162.222.119:8080/api/v1/resolve?app_id=2090640174649008128` 获取 `polyvision` 真实 endpoint；
- 拉取 `/api/v1/app/client/auth/bootstrap.jpg`，解析 JPEG 尾部隐写载荷；
- 调用 `libmagic_cipher.so` 的 `nativeBootstrapDeriveCandidateKeys`、AES-256-CBC + HMAC-SHA256 解密 bootstrap；
- 请求签名 `MD5(timestamp + nonce + appId + signSecret)`；
- 设备指纹 `maesProcess` + `mbase64Process` + HMAC-SHA256；
- 请求体 `encryptV3` / 响应 `decryptV3`；
- 首页 `/home/recommend`，详情 `/video/detail`，播放解析 `/resolve/qualities` + `/resolve/exchange`；
- 直链 / HLS playlist 播放（`VideoView`）。

## 限制

- 只播放第一集，且只取第一个播放源/第一个清晰度；
- 如果服务端返回 `luaScript`/`clientScript` 而没有直链或 playlist，当前版本会提示“需要 Lua 解析”；
- 登录、VIP、广告 token、弹幕、下载、投屏均未实现；
- 仅用于授权靶场/自有服务的技术验证。
