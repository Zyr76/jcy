import 'dart:convert';

import 'package:flutter/material.dart';

import 'native/gateway.dart';

void main() {
  runApp(const ProbeApp());
}

class ProbeApp extends StatelessWidget {
  const ProbeApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '协议探针',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(useMaterial3: true).copyWith(
        scaffoldBackgroundColor: const Color(0xFF0B0C10),
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF7C5CFF),
          brightness: Brightness.dark,
        ),
      ),
      home: const ProbePage(),
    );
  }
}

class ProbePage extends StatefulWidget {
  const ProbePage({super.key});

  @override
  State<ProbePage> createState() => _ProbePageState();
}

class _ProbePageState extends State<ProbePage> {
  static const filesPath = '/data/user/0/com.tingfeng.tool/files';

  final List<String> logs = [];
  Map<String, dynamic>? appInfo;
  Map<String, dynamic>? hostConfig;
  bool busy = false;

  void log(String text) => setState(() => logs.add(text));

  Future<void> run(String label, Map<String, dynamic> Function() task) async {
    setState(() => busy = true);
    log('[b3] ▶ $label');
    try {
      final result = await Future<Map<String, dynamic>>.delayed(
        Duration.zero,
        task,
      );
      log(const JsonEncoder.withIndent('  ').convert(result));
    } catch (error, stack) {
      log('✗ $error');
      log(stack.toString().split('\n').take(6).join('\n'));
    } finally {
      setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('协议探针 b3'),
        backgroundColor: const Color(0xFF12141C),
        actions: [
          IconButton(
            onPressed: () => setState(logs.clear),
            icon: const Icon(Icons.delete_outline),
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _button('get_app_info', () => run('get_app_info', () {
                      final result = Gateway.throughLoader('get_app_info');
                      final payload = result['payload'];
                      if (payload is Map) {
                        appInfo = Map<String, dynamic>.from(payload);
                      }
                      return result;
                    })),
                _button('get_host_config', () => run('get_host_config', () {
                      final result = Gateway.throughLoader('get_host_config');
                      final payload = result['payload'];
                      if (payload is Map) {
                        hostConfig = Map<String, dynamic>.from(payload);
                      }
                      return result;
                    })),
                _button('get_registries', () =>
                    run('get_registries', () => Gateway.throughLoader('get_registries'))),
                _button('core:get_app_info', () =>
                    run('core:get_app_info', () => Gateway.throughCore('get_app_info'))),
                _button('init', () => run('init', () {
                      return Gateway.init(
                        appInfo: appInfo ?? {'app_id': '', 'device_id': ''},
                        hostConfig: hostConfig ?? {'tcp': ''},
                        filesPath: filesPath,
                      );
                    })),
              ],
            ),
          ),
          if (busy) const LinearProgressIndicator(minHeight: 2),
          Container(
            width: double.infinity,
            color: const Color(0xFF182030),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            child: const Text(
              '版本 0.1.3 / build b3 —— 若标题不是 b3 说明装的是旧包',
              style: TextStyle(fontSize: 11, color: Color(0xFF9FB0C9)),
            ),
          ),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(12),
              child: SelectableText(
                logs.isEmpty ? '点击上方按钮进行探测，结果会显示在这里。' : logs.join('\n\n'),
                style: const TextStyle(
                  fontFamily: 'monospace',
                  fontSize: 11,
                  height: 1.45,
                  color: Color(0xFFD8DCE8),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _button(String label, VoidCallback onTap) {
    return FilledButton.tonal(
      onPressed: busy ? null : onTap,
      child: Text(label, style: const TextStyle(fontSize: 12)),
    );
  }
}
