# 囧次元客户端（Flutter）

基于逆向分析结果重写的客户端：**逻辑层与 native 核完全复用，UI 层用 Flutter 全新实现**。

## 结构

```
lib/                 Flutter UI 层（全新）
  main.dart          入口、深色主题
  theme.dart         配色与 Material 主题
  logic.dart         MethodChannel 客户端（home / resolve）
  models.dart        VideoItem / PlayInfo
  screens/           首页、播放页
  widgets/           焦点轮播、海报卡、骨架屏
android/             Android 宿主
  app/src/main/java/com/jcy/client/
    api/ crypto/ model/ Session/ApiConfig   ← 原逻辑层，未改动
    MagicInit + jniLibs/libmagic_cipher.so  ← 原 native 核
    LogicBridge.java  把逻辑层暴露为 MethodChannel
    MainActivity.java FlutterActivity 宿主
```

## 界面

- 深色影院风首页：自动轮播焦点卡（评分/更新徽标、渐变遮罩）、双列海报网格、滚动渐显顶栏；
- 骨架屏加载、下拉刷新、错误重试；
- 全屏播放器：自定义控制层（拖动进度、播放/暂停、全屏横屏）、缓冲与错误状态；
- 自适应/矢量图标、沉浸式状态栏。

## 构建

本地（arm64 只能出 debug）：

```bash
flutter pub get
flutter build apk --debug --target-platform android-arm64
```

推到 GitHub 后由 `.github/workflows/android.yml` 在 x64 runner 上出 release（按 ABI 拆分），产物为 `jcy-client-release` artifact。

## 限制

- 逻辑层保持原型能力：只播放第一集、取第一个播放源/清晰度；
- 登录、VIP、广告 token、弹幕、下载、投屏未实现；
- 仅用于授权靶场/自有服务的技术验证。
