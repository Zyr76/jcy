import 'package:flutter/material.dart';

import '../models.dart';
import '../theme.dart';

class PosterCard extends StatefulWidget {
  const PosterCard({super.key, required this.item, required this.onTap});

  final VideoItem item;
  final VoidCallback onTap;

  @override
  State<PosterCard> createState() => _PosterCardState();
}

class _PosterCardState extends State<PosterCard> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapCancel: () => setState(() => _pressed = false),
      onTapUp: (_) => setState(() => _pressed = false),
      onTap: widget.onTap,
      child: AnimatedScale(
        scale: _pressed ? 0.96 : 1,
        duration: const Duration(milliseconds: 140),
        curve: Curves.easeOut,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    const ColoredBox(color: AppColors.card),
                    if (item.cover.isNotEmpty)
                      Image.network(
                        item.cover,
                        fit: BoxFit.cover,
                        errorBuilder: (context, error, stack) => _placeholder(),
                      )
                    else
                      _placeholder(),
                    const DecoratedBox(
                      decoration: BoxDecoration(gradient: AppColors.posterScrim),
                    ),
                    if (item.score > 0)
                      Positioned(
                        top: 8,
                        right: 8,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: AppColors.gold,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            item.score.toStringAsFixed(1),
                            style: const TextStyle(
                              color: Color(0xFF2A2008),
                              fontSize: 10,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ),
                    if (item.episodeText.isNotEmpty)
                      Positioned(
                        left: 10,
                        right: 10,
                        bottom: 9,
                        child: Text(
                          item.episodeText,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: AppColors.text,
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    if (item.progress != null && item.progress! < 1)
                      Positioned(
                        left: 0,
                        right: 0,
                        bottom: 0,
                        child: LinearProgressIndicator(
                          value: item.progress,
                          minHeight: 3,
                          backgroundColor: const Color(0x33FFFFFF),
                          valueColor: const AlwaysStoppedAnimation(
                              AppColors.accent2),
                        ),
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              item.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: AppColors.text,
                fontSize: 13,
                fontWeight: FontWeight.w600,
                height: 1.25,
              ),
            ),
            if (item.subTitle.isNotEmpty || item.score > 0) ...[
              const SizedBox(height: 3),
              Text(
                item.subTitle.isNotEmpty
                    ? item.subTitle
                    : '${item.score.toStringAsFixed(1)} 分',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                    color: AppColors.textMuted, fontSize: 11),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _placeholder() {
    return const Center(
      child: Icon(Icons.movie_outlined, color: Color(0x33FFFFFF), size: 26),
    );
  }
}
