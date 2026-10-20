import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';

import '../logic.dart';
import '../models.dart';
import '../theme.dart';

class PlayerScreen extends StatefulWidget {
  const PlayerScreen({super.key, required this.item});

  final VideoItem item;

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  VideoPlayerController? _controller;
  PlayInfo? _info;
  String? _error;
  bool _resolving = true;
  bool _controlsVisible = true;
  bool _fullscreen = false;
  double? _dragMs;
  Timer? _hideTimer;

  @override
  void initState() {
    super.initState();
    _resolve();
  }

  @override
  void dispose() {
    _hideTimer?.cancel();
    _controller?.removeListener(_onTick);
    _controller?.dispose();
    _restoreSystemUi();
    super.dispose();
  }

  Future<void> _resolve() async {
    setState(() {
      _resolving = true;
      _error = null;
    });
    try {
      final info = await Logic.resolve(widget.item.id);
      final controller = info.localPlaylist
          ? VideoPlayerController.file(File(info.url))
          : VideoPlayerController.networkUrl(Uri.parse(info.url));
      await controller.initialize();
      if (!mounted) {
        controller.dispose();
        return;
      }
      controller.addListener(_onTick);
      setState(() {
        _info = info;
        _controller = controller;
        _resolving = false;
      });
      await controller.play();
      _scheduleHide();
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error.toString();
        _resolving = false;
      });
    }
  }

  void _onTick() {
    if (!mounted) return;
    final controller = _controller;
    if (controller == null) return;
    final value = controller.value;
    if (value.hasError && _error == null) {
      setState(() => _error = value.errorDescription ?? '播放失败');
      return;
    }
    setState(() {});
  }

  void _toggleControls() {
    setState(() => _controlsVisible = !_controlsVisible);
    if (_controlsVisible) {
      _scheduleHide();
    } else {
      _hideTimer?.cancel();
    }
  }

  void _scheduleHide() {
    _hideTimer?.cancel();
    if (!_controlsVisible) return;
    _hideTimer = Timer(const Duration(milliseconds: 3500), () {
      if (!mounted) return;
      setState(() => _controlsVisible = false);
    });
  }

  Future<void> _togglePlay() async {
    final controller = _controller;
    if (controller == null) return;
    if (controller.value.isPlaying) {
      await controller.pause();
    } else {
      if (controller.value.position >= controller.value.duration) {
        await controller.seekTo(Duration.zero);
      }
      await controller.play();
    }
    _scheduleHide();
  }

  Future<void> _toggleFullscreen() async {
    final next = !_fullscreen;
    setState(() => _fullscreen = next);
    if (next) {
      await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
      await SystemChrome.setPreferredOrientations([
        DeviceOrientation.landscapeLeft,
        DeviceOrientation.landscapeRight,
      ]);
    } else {
      _restoreSystemUi();
    }
    _scheduleHide();
  }

  void _restoreSystemUi() {
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    final topInset = MediaQuery.paddingOf(context).top;
    return PopScope(
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) _restoreSystemUi();
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        body: Stack(
          children: [
            Positioned.fill(child: _videoLayer(controller)),
            Positioned.fill(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: _toggleControls,
              ),
            ),
            if (_resolving) _loading(),
            if (_error != null) _errorPanel(),
            if (controller != null &&
                !_resolving &&
                !controller.value.isInitialized &&
                _error == null)
              const Center(
                child: CircularProgressIndicator(color: AppColors.accent2),
              ),
            if (controller != null &&
                controller.value.isBuffering &&
                _error == null &&
                !_resolving)
              const Center(
                child: SizedBox(
                  width: 36,
                  height: 36,
                  child: CircularProgressIndicator(
                    strokeWidth: 2.5,
                    color: AppColors.accent2,
                  ),
                ),
              ),
            if (controller != null && _error == null)
              AnimatedOpacity(
                opacity: _controlsVisible ? 1 : 0,
                duration: const Duration(milliseconds: 200),
                child: IgnorePointer(
                  ignoring: !_controlsVisible,
                  child: _controls(controller, topInset),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _videoLayer(VideoPlayerController? controller) {
    if (controller == null || !controller.value.isInitialized) {
      return const ColoredBox(color: Colors.black);
    }
    final size = controller.value.size;
    return Center(
      child: AspectRatio(
        aspectRatio: size.width == 0 || size.height == 0
            ? 16 / 9
            : size.width / size.height,
        child: VideoPlayer(controller),
      ),
    );
  }

  Widget _loading() {
    return ColoredBox(
      color: Colors.black,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(
              width: 28,
              height: 28,
              child: CircularProgressIndicator(
                strokeWidth: 2.5,
                color: AppColors.accent2,
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              '正在解析播放地址…',
              style: TextStyle(color: AppColors.textMuted, fontSize: 12),
            ),
            const SizedBox(height: 6),
            Text(
              widget.item.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: AppColors.text, fontSize: 13),
            ),
            const SizedBox(height: 22),
            Material(
              color: Colors.white.withValues(alpha: 0.12),
              shape: const StadiumBorder(),
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: () => Navigator.of(context).maybePop(),
                child: const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 22, vertical: 8),
                  child: Text(
                    '取消',
                    style: TextStyle(color: AppColors.text, fontSize: 12),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _errorPanel() {
    return ColoredBox(
      color: Colors.black,
      child: Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                '无法播放',
                style: TextStyle(
                  color: AppColors.text,
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                _error!,
                maxLines: 5,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 12,
                  height: 1.5,
                ),
              ),
              const SizedBox(height: 20),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _pill('重试', AppColors.accentGradient, () {
                    _controller?.dispose();
                    _controller = null;
                    _resolve();
                  }),
                  const SizedBox(width: 12),
                  _pill(
                    '返回',
                    const LinearGradient(
                      colors: [Color(0x33FFFFFF), Color(0x33FFFFFF)],
                    ),
                    () => Navigator.of(context).maybePop(),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _pill(String label, Gradient gradient, VoidCallback onTap) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: gradient,
        borderRadius: BorderRadius.circular(22),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(22),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 10),
            child: Text(
              label,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 13,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _controls(VideoPlayerController controller, double topInset) {
    final value = controller.value;
    final duration = value.duration;
    final maxMs = duration.inMilliseconds.toDouble().clamp(1.0, double.infinity);
    final currentMs =
        (_dragMs ?? value.position.inMilliseconds.toDouble()).clamp(0.0, maxMs);

    return Stack(
      children: [
        Positioned(
          top: 0,
          left: 0,
          right: 0,
          child: IgnorePointer(
            child: Container(
              height: 130 + topInset,
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Color(0xCC000000), Color(0x00000000)],
                ),
              ),
            ),
          ),
        ),
        Positioned(
          top: topInset + 6,
          left: 8,
          right: 16,
          child: Row(
            children: [
              IconButton(
                icon: const Icon(
                  Icons.arrow_back_ios_new_rounded,
                  size: 18,
                  color: AppColors.text,
                ),
                onPressed: () => Navigator.of(context).maybePop(),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      (_info?.title.isNotEmpty ?? false)
                          ? _info!.title
                          : widget.item.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: AppColors.text,
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    Text(
                      _info?.localPlaylist == true ? '本地 HLS 播放列表' : '在线播放',
                      style: const TextStyle(
                        color: AppColors.textMuted,
                        fontSize: 11,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        Positioned(
          left: 0,
          right: 0,
          bottom: 0,
          child: IgnorePointer(
            child: Container(
              height: 160,
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.bottomCenter,
                  end: Alignment.topCenter,
                  colors: [Color(0xE6000000), Color(0x00000000)],
                ),
              ),
            ),
          ),
        ),
        Positioned(
          left: 8,
          right: 8,
          bottom: MediaQuery.paddingOf(context).bottom + 4,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Slider(
                value: currentMs,
                max: maxMs,
                onChangeStart: (value) {
                  _hideTimer?.cancel();
                },
                onChanged: (value) {
                  setState(() => _dragMs = value);
                },
                onChangeEnd: (value) {
                  controller.seekTo(Duration(milliseconds: value.round()));
                  setState(() => _dragMs = null);
                  _scheduleHide();
                },
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Row(
                  children: [
                    Material(
                      color: Colors.white.withValues(alpha: 0.14),
                      shape: const CircleBorder(),
                      clipBehavior: Clip.antiAlias,
                      child: InkWell(
                        onTap: _togglePlay,
                        child: Padding(
                          padding: const EdgeInsets.all(8),
                          child: Icon(
                            value.isPlaying
                                ? Icons.pause_rounded
                                : Icons.play_arrow_rounded,
                            color: Colors.white,
                            size: 22,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Text(
                      _format(value.position),
                      style: const TextStyle(
                          color: AppColors.text, fontSize: 11),
                    ),
                    const Text(
                      ' / ',
                      style: TextStyle(
                          color: AppColors.textMuted, fontSize: 11),
                    ),
                    Text(
                      _format(duration),
                      style: const TextStyle(
                          color: AppColors.textMuted, fontSize: 11),
                    ),
                    const Spacer(),
                    Material(
                      color: Colors.white.withValues(alpha: 0.14),
                      shape: const StadiumBorder(),
                      clipBehavior: Clip.antiAlias,
                      child: InkWell(
                        onTap: _toggleFullscreen,
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 14, vertical: 8),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                _fullscreen
                                    ? Icons.fullscreen_exit_rounded
                                    : Icons.fullscreen_rounded,
                                color: Colors.white,
                                size: 18,
                              ),
                              const SizedBox(width: 4),
                              Text(
                                _fullscreen ? '退出全屏' : '全屏',
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  static String _format(Duration duration) {
    final total = duration.inSeconds.clamp(0, 359999);
    final minutes = (total ~/ 60).toString().padLeft(2, '0');
    final seconds = (total % 60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }
}
