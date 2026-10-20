import 'package:flutter/services.dart';

import 'models.dart';

class LogicException implements Exception {
  LogicException(this.message);

  final String message;

  @override
  String toString() => message;
}

class Logic {
  static const _channel = MethodChannel('com.jcy.client/logic');

  static Future<List<VideoItem>> home() async {
    try {
      final raw = await _channel.invokeMethod<List<dynamic>>('home');
      return (raw ?? const [])
          .whereType<Map<dynamic, dynamic>>()
          .map(VideoItem.fromMap)
          .where((item) => item.id.isNotEmpty)
          .toList(growable: false);
    } on PlatformException catch (error) {
      throw LogicException(error.message ?? '加载失败');
    }
  }

  static Future<PlayInfo> resolve(String videoId) async {
    try {
      final raw = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'resolve',
        {'videoId': videoId},
      );
      if (raw == null) {
        throw LogicException('解析接口没有返回数据');
      }
      return PlayInfo.fromMap(raw);
    } on PlatformException catch (error) {
      throw LogicException(error.message ?? '解析失败');
    }
  }
}
