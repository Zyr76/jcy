import 'package:flutter/material.dart';

import '../logic.dart';
import '../models.dart';
import '../theme.dart';
import '../widgets/hero_banner.dart';
import '../widgets/poster_card.dart';
import '../widgets/skeleton.dart';
import 'player_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final ScrollController _scroll = ScrollController();
  List<VideoItem>? _items;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    if (_items == null) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final items = await Logic.home();
      if (!mounted) return;
      setState(() {
        _items = items;
        _error = items.isEmpty ? '首页没有返回内容' : null;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error.toString();
        _loading = false;
      });
    }
  }

  void _open(VideoItem item) {
    Navigator.of(context).push(
      PageRouteBuilder(
        transitionDuration: const Duration(milliseconds: 260),
        pageBuilder: (context, animation, secondary) => PlayerScreen(item: item),
        transitionsBuilder: (context, animation, secondary, child) =>
            FadeTransition(opacity: animation, child: child),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final topInset = MediaQuery.paddingOf(context).top;
    return Scaffold(
      backgroundColor: AppColors.bg,
      body: Stack(
        children: [
          Positioned.fill(
            child: RefreshIndicator(
              onRefresh: _load,
              color: AppColors.accent,
              backgroundColor: AppColors.surface,
              child: _body(context, topInset),
            ),
          ),
          Positioned(
            left: 0,
            right: 0,
            top: 0,
            child: _TopBar(
              scroll: _scroll,
              topInset: topInset,
              onRefresh: _load,
            ),
          ),
        ],
      ),
    );
  }

  Widget _body(BuildContext context, double topInset) {
    if (_loading) {
      final heroHeight =
          ((MediaQuery.sizeOf(context).width - 32) * 0.74).clamp(220.0, 340.0);
      return CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          SliverToBoxAdapter(child: SizedBox(height: topInset + 76)),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverToBoxAdapter(
              child: SkeletonBox(height: heroHeight, radius: 22),
            ),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 26)),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 18),
            sliver: SliverToBoxAdapter(
              child: SkeletonBox(height: 16, width: 96, radius: 6),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 40),
            sliver: SliverGrid(
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                crossAxisSpacing: 12,
                mainAxisSpacing: 18,
                childAspectRatio: 0.56,
              ),
              delegate: SliverChildBuilderDelegate(
                (context, index) => Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: const [
                    Expanded(child: SkeletonBox(radius: 16)),
                    SizedBox(height: 8),
                    SkeletonBox(height: 12, width: 110, radius: 6),
                    SizedBox(height: 6),
                    SkeletonBox(height: 10, width: 70, radius: 5),
                  ],
                ),
                childCount: 4,
              ),
            ),
          ),
        ],
      );
    }

    if (_error != null || _items == null) {
      return LayoutBuilder(
        builder: (context, constraints) => SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: constraints.maxHeight),
            child: Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 32),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.cloud_off_rounded,
                        color: AppColors.danger, size: 44),
                    const SizedBox(height: 12),
                    const Text(
                      '内容加载失败',
                      style: TextStyle(
                        color: AppColors.text,
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _error ?? '未知错误',
                      textAlign: TextAlign.center,
                      maxLines: 4,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: AppColors.textMuted,
                        fontSize: 12,
                        height: 1.5,
                      ),
                    ),
                    const SizedBox(height: 20),
                    _GradientButton(label: '重新加载', onTap: _load),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    }

    final items = _items!;
    final heroCount =
        items.length > 8 ? 6 : (items.length >= 2 ? 2 : items.length);
    final heroItems = items.take(heroCount).toList(growable: false);
    final gridItems = items.skip(heroCount).toList(growable: false);

    return CustomScrollView(
      controller: _scroll,
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        SliverToBoxAdapter(child: SizedBox(height: topInset + 76)),
        SliverToBoxAdapter(child: HeroBanner(items: heroItems, onTap: _open)),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(18, 26, 18, 12),
            child: Row(
              children: [
                Container(
                  width: 4,
                  height: 16,
                  decoration: BoxDecoration(
                    gradient: AppColors.accentGradient,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                const SizedBox(width: 8),
                const Expanded(
                  child: Text(
                    '为你推荐',
                    style: TextStyle(
                      color: AppColors.text,
                      fontSize: 18,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
                Text(
                  '${gridItems.length}',
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 12),
                ),
              ],
            ),
          ),
        ),
        SliverPadding(
          padding: EdgeInsets.fromLTRB(
            16,
            0,
            16,
            32 + MediaQuery.paddingOf(context).bottom,
          ),
          sliver: SliverGrid(
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              crossAxisSpacing: 12,
              mainAxisSpacing: 18,
              childAspectRatio: 0.56,
            ),
            delegate: SliverChildBuilderDelegate(
              (context, index) {
                final item = gridItems[index];
                return PosterCard(item: item, onTap: () => _open(item));
              },
              childCount: gridItems.length,
            ),
          ),
        ),
      ],
    );
  }
}

class _TopBar extends StatelessWidget {
  const _TopBar({
    required this.scroll,
    required this.topInset,
    required this.onRefresh,
  });

  final ScrollController scroll;
  final double topInset;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: scroll,
      builder: (context, child) {
        final offset = scroll.hasClients ? scroll.offset : 0.0;
        final t = (offset / 90).clamp(0.0, 1.0);
        return Container(
          padding: EdgeInsets.fromLTRB(18, topInset + 6, 14, 10),
          decoration: BoxDecoration(
            color: AppColors.bg.withValues(alpha: 0.92 * t),
            border: Border(
              bottom: BorderSide(
                color: Colors.white.withValues(alpha: 0.06 * t),
              ),
            ),
          ),
          child: child,
        );
      },
      child: Row(
        children: [
          const Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                '囧次元',
                style: TextStyle(
                  color: AppColors.text,
                  fontSize: 20,
                  fontWeight: FontWeight.w900,
                  letterSpacing: 0.3,
                ),
              ),
              SizedBox(height: 1),
              Text(
                '发现 · 追剧 · 收藏',
                style: TextStyle(color: AppColors.textMuted, fontSize: 11),
              ),
            ],
          ),
          const Spacer(),
          Material(
            color: Colors.white.withValues(alpha: 0.1),
            shape: const StadiumBorder(),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: onRefresh,
              child: const Padding(
                padding: EdgeInsets.symmetric(horizontal: 14, vertical: 7),
                child: Text(
                  '刷新',
                  style: TextStyle(
                    color: AppColors.text,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _GradientButton extends StatelessWidget {
  const _GradientButton({required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: AppColors.accentGradient,
        borderRadius: BorderRadius.circular(22),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(22),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 26, vertical: 10),
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
}
