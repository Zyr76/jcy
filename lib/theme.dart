import 'package:flutter/material.dart';

class AppColors {
  static const bg = Color(0xFF08090D);
  static const card = Color(0xFF12141C);
  static const surface = Color(0xFF181B26);
  static const text = Color(0xFFF4F6FB);
  static const textMuted = Color(0xFF8B90A3);
  static const accent = Color(0xFF7C5CFF);
  static const accent2 = Color(0xFF3BE1F1);
  static const danger = Color(0xFFFF5C7A);
  static const gold = Color(0xFFFFC857);

  static const accentGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [accent, Color(0xFFB45CFF)],
  );

  static const posterScrim = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [Color(0x00000000), Color(0xCC08090D)],
  );

  static const heroScrim = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    stops: [0.15, 0.62, 1],
    colors: [Color(0x00000000), Color(0x9908090D), Color(0xF508090D)],
  );
}

ThemeData buildTheme() {
  const scheme = ColorScheme.dark(
    primary: AppColors.accent,
    secondary: AppColors.accent2,
    surface: AppColors.card,
    error: AppColors.danger,
    onPrimary: Colors.white,
    onSurface: AppColors.text,
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: AppColors.bg,
    canvasColor: AppColors.bg,
    textTheme: const TextTheme(
      headlineSmall: TextStyle(
        color: AppColors.text,
        fontWeight: FontWeight.w900,
        letterSpacing: 0.2,
      ),
      titleMedium: TextStyle(color: AppColors.text, fontWeight: FontWeight.w600),
      bodySmall: TextStyle(color: AppColors.textMuted),
    ),
    sliderTheme: SliderThemeData(
      activeTrackColor: AppColors.accent2,
      inactiveTrackColor: const Color(0x33FFFFFF),
      thumbColor: Colors.white,
      overlayColor: AppColors.accent.withValues(alpha: 0.16),
      trackHeight: 3,
      thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
      overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
    ),
  );
}
