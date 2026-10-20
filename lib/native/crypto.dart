import 'dart:convert';
import 'dart:typed_data';

import 'package:pointycastle/export.dart';

class AesCbc {
  static Uint8List _bytes(String text) => Uint8List.fromList(utf8.encode(text));

  static Uint8List encrypt(String plainText, String key, String iv) {
    final cipher = PaddedBlockCipherImpl(PKCS7Padding(), CBCBlockCipher(AESEngine()));
    cipher.init(
      true,
      PaddedBlockCipherParameters(
        ParametersWithIV(KeyParameter(_bytes(key)), _bytes(iv)),
        null,
      ),
    );
    return cipher.process(_bytes(plainText));
  }

  static String decrypt(String base64Text, String key, String iv) {
    final cipher = PaddedBlockCipherImpl(PKCS7Padding(), CBCBlockCipher(AESEngine()));
    cipher.init(
      false,
      PaddedBlockCipherParameters(
        ParametersWithIV(KeyParameter(_bytes(key)), _bytes(iv)),
        null,
      ),
    );
    final out = cipher.process(base64.decode(base64Text));
    return utf8.decode(out);
  }
}
