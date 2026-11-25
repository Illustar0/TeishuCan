import 'package:material_ui/material_ui.dart';

enum AppThemeMode {
  system,
  light,
  dark;

  bool isDark(Brightness platformBrightness) => switch (this) {
        AppThemeMode.system => platformBrightness == Brightness.dark,
        AppThemeMode.light => false,
        AppThemeMode.dark => true,
      };
}

enum SeedColor {
  blue(Color(0xFF3482FF)),
  green(Color(0xFF36D167)),
  purple(Color(0xFF7C4DFF)),
  yellow(Color(0xFFFFB21D)),
  orange(Color(0xFFFF5722)),
  pink(Color(0xFFE91E63)),
  teal(Color(0xFF00BCD4)),
  red(Color(0xFFE53935));

  const SeedColor(this.color);
  final Color color;
}

enum PaletteStyle {
  tonalSpot,
  neutral,
  vibrant,
  expressive,
  rainbow,
  fruitSalad,
  monochrome,
  fidelity,
  content,
}

enum AppLanguage {
  system,
  japanese,
  chinese,
}

class ThemeSettings {
  const ThemeSettings({
    this.mode = AppThemeMode.system,
    this.isMonetEnabled = false,
    this.seedColor = SeedColor.blue,
    this.paletteStyle = PaletteStyle.tonalSpot,
    this.isBlurEnabled = true,
    this.isFloatingNavigationBarEnabled = false,
    this.isLiquidGlassEnabled = false,
  });

  factory ThemeSettings.fromJson(Map<String, dynamic> json) {
    return ThemeSettings(
      mode: _enumByName(AppThemeMode.values, json['mode'], AppThemeMode.system),
      isMonetEnabled: json['isMonetEnabled'] as bool? ?? false,
      seedColor:
          _enumByName(SeedColor.values, json['seedColor'], SeedColor.blue),
      paletteStyle: _enumByName(
        PaletteStyle.values,
        json['paletteStyle'],
        PaletteStyle.tonalSpot,
      ),
      isBlurEnabled: json['isBlurEnabled'] as bool? ?? true,
      isFloatingNavigationBarEnabled:
          json['isFloatingNavigationBarEnabled'] as bool? ?? false,
      isLiquidGlassEnabled: json['isLiquidGlassEnabled'] as bool? ?? false,
    );
  }

  final AppThemeMode mode;
  final bool isMonetEnabled;
  final SeedColor seedColor;
  final PaletteStyle paletteStyle;
  final bool isBlurEnabled;
  final bool isFloatingNavigationBarEnabled;
  final bool isLiquidGlassEnabled;

  ThemeSettings copyWith({
    AppThemeMode? mode,
    bool? isMonetEnabled,
    SeedColor? seedColor,
    PaletteStyle? paletteStyle,
    bool? isBlurEnabled,
    bool? isFloatingNavigationBarEnabled,
    bool? isLiquidGlassEnabled,
  }) =>
      ThemeSettings(
        mode: mode ?? this.mode,
        isMonetEnabled: isMonetEnabled ?? this.isMonetEnabled,
        seedColor: seedColor ?? this.seedColor,
        paletteStyle: paletteStyle ?? this.paletteStyle,
        isBlurEnabled: isBlurEnabled ?? this.isBlurEnabled,
        isFloatingNavigationBarEnabled:
            isFloatingNavigationBarEnabled ?? this.isFloatingNavigationBarEnabled,
        isLiquidGlassEnabled: isLiquidGlassEnabled ?? this.isLiquidGlassEnabled,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'mode': mode.name,
        'isMonetEnabled': isMonetEnabled,
        'seedColor': seedColor.name,
        'paletteStyle': paletteStyle.name,
        'isBlurEnabled': isBlurEnabled,
        'isFloatingNavigationBarEnabled': isFloatingNavigationBarEnabled,
        'isLiquidGlassEnabled': isLiquidGlassEnabled,
      };

  static T _enumByName<T extends Enum>(List<T> values, Object? raw, T fallback) {
    if (raw is! String) return fallback;
    for (final value in values) {
      if (value.name == raw) return value;
    }
    return fallback;
  }
}
