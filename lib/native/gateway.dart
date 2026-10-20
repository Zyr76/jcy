import 'dart:convert';
import 'dart:ffi';

import 'package:ffi/ffi.dart';

import 'crypto.dart';

class Gateway {
  static const loaderKey = 'kFGTbLlOzFHQCIKp';
  static const loaderIv = 'F3q22XoM8l6T2Ydc';
  static const coreKey = 'qPwClBj7j7ZQraSm';
  static const coreIv = 'p3JdVQl3q7WQJIgG';

  static final DynamicLibrary _loader = DynamicLibrary.open('libloader.so');
  static final DynamicLibrary _core = DynamicLibrary.open('libcore.so');

  static final _callLoader = _loader.lookupFunction<
      Pointer<Utf8> Function(Pointer<Utf8>),
      Pointer<Utf8> Function(Pointer<Utf8>)>('call');

  static final _callCore = _core.lookupFunction<
      Pointer<Utf8> Function(Pointer<Utf8>),
      Pointer<Utf8> Function(Pointer<Utf8>)>('call');

  static final _init = _core.lookupFunction<Void Function(Pointer<Utf8>),
      void Function(Pointer<Utf8>)>('init');

  static Map<String, dynamic> throughLoader(String action,
          {Map<String, dynamic>? extra}) =>
      _invoke(_callLoader, action, loaderKey, loaderIv, extra);

  static Map<String, dynamic> throughCore(String action,
          {Map<String, dynamic>? extra}) =>
      _invoke(_callCore, action, coreKey, coreIv, extra);

  static Map<String, dynamic> _invoke(
    Pointer<Utf8> Function(Pointer<Utf8>) fn,
    String action,
    String key,
    String iv, [
    Map<String, dynamic>? extra,
  ]) {
    final request = <String, dynamic>{'action': action, ...?extra};
    final plain = jsonEncode(request);
    final cipher = base64.encode(AesCbc.encrypt(plain, key, iv));
    final pointer = cipher.toNativeUtf8();
    Pointer<Utf8> response;
    try {
      response = fn(pointer);
    } finally {
      malloc.free(pointer);
    }
    if (response == nullptr) {
      return {'request': request, 'error': 'native 返回空指针'};
    }
    final raw = response.toDartString();
    malloc.free(response);

    final out = <String, dynamic>{
      'request': request,
      'request_cipher': cipher,
      'response_raw': raw,
    };
    try {
      final envelope = jsonDecode(AesCbc.decrypt(raw, key, iv)) as Map;
      out['envelope'] = envelope;
      out['payload'] = _deref(envelope['payload']);
      out['payload_text'] = _lastText;
    } catch (error) {
      out['decrypt_error'] = error.toString();
    }
    return out;
  }

  static String? _lastText;

  /// 原生把真正的结果放在进程堆上，响应里只给指针的十六进制地址。
  static dynamic _deref(dynamic payload) {
    _lastText = null;
    if (payload is! Map) return payload;
    final address = payload['address'];
    if (address is! String || address.isEmpty) return payload;
    final int? addr = int.tryParse(address, radix: 16);
    if (addr == null || addr == 0) return payload;
    _lastText = Pointer<Utf8>.fromAddress(addr).toDartString();
    try {
      return jsonDecode(_lastText!);
    } catch (_) {
      return _lastText;
    }
  }

  static Map<String, dynamic> init({
    required Map<String, dynamic> appInfo,
    required Map<String, dynamic> hostConfig,
    required String filesPath,
  }) {
    final payload = <String, dynamic>{
      'app_id': appInfo['app_id'] ?? '',
      'device_id': appInfo['device_id'] ?? '',
      'code_version': appInfo['code_version'] ?? '',
      'app_version': appInfo['app_version'] ?? '',
      'files_path': filesPath,
      'tcp': hostConfig['tcp'] ?? '',
    };
    final plain = jsonEncode(payload);
    final pointer = plain.toNativeUtf8();
    try {
      _init(pointer);
    } finally {
      malloc.free(pointer);
    }
    return {'request': payload, 'sent_plaintext': plain};
  }
}
