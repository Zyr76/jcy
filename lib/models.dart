class VideoItem {
  const VideoItem({
    required this.id,
    required this.title,
    required this.cover,
    required this.subTitle,
    required this.currentEpisodes,
    required this.totalEpisodes,
    required this.score,
  });

  final String id;
  final String title;
  final String cover;
  final String subTitle;
  final int currentEpisodes;
  final int totalEpisodes;
  final double score;

  factory VideoItem.fromMap(Map<dynamic, dynamic> map) {
    return VideoItem(
      id: (map['id'] ?? '').toString(),
      title: (map['title'] ?? '未命名').toString(),
      cover: (map['cover'] ?? '').toString(),
      subTitle: (map['subTitle'] ?? '').toString(),
      currentEpisodes: (map['currentEpisodes'] as num?)?.toInt() ?? 0,
      totalEpisodes: (map['totalEpisodes'] as num?)?.toInt() ?? 0,
      score: (map['score'] as num?)?.toDouble() ?? 0,
    );
  }

  String get episodeText {
    if (totalEpisodes > 0) {
      return '更新至 $currentEpisodes / $totalEpisodes 集';
    }
    return currentEpisodes > 0 ? '更新至 $currentEpisodes 集' : '';
  }

  double? get progress {
    if (totalEpisodes <= 0) return null;
    return (currentEpisodes / totalEpisodes).clamp(0.0, 1.0);
  }
}

class PlayInfo {
  const PlayInfo({
    required this.title,
    required this.url,
    required this.localPlaylist,
  });

  final String title;
  final String url;
  final bool localPlaylist;

  factory PlayInfo.fromMap(Map<dynamic, dynamic> map) {
    return PlayInfo(
      title: (map['title'] ?? '').toString(),
      url: (map['url'] ?? '').toString(),
      localPlaylist: map['localPlaylist'] == true,
    );
  }
}
