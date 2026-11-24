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

enum AppLanguage {
  system,
  japanese,
  chinese,
}

class ThemeSettings {
  const ThemeSettings({
    this.mode = AppThemeMode.system,
    this.seedColor = SeedColor.blue,
    this.isBlurEnabled = true,
    this.isFloatingNavigationBarEnabled = false,
  });

  factory ThemeSettings.fromJson(Map<String, dynamic> json) {
    return ThemeSettings(
      mode: _enumByName(AppThemeMode.values, json['mode'], AppThemeMode.system),
      seedColor:
          _enumByName(SeedColor.values, json['seedColor'], SeedColor.blue),
      isBlurEnabled: json['isBlurEnabled'] as bool? ?? true,
      isFloatingNavigationBarEnabled:
          json['isFloatingNavigationBarEnabled'] as bool? ?? false,
    );
  }

  final AppThemeMode mode;
  final SeedColor seedColor;
  final bool isBlurEnabled;
  final bool isFloatingNavigationBarEnabled;

  ThemeSettings copyWith({
    AppThemeMode? mode,
    SeedColor? seedColor,
    bool? isBlurEnabled,
    bool? isFloatingNavigationBarEnabled,
  }) =>
      ThemeSettings(
        mode: mode ?? this.mode,
        seedColor: seedColor ?? this.seedColor,
        isBlurEnabled: isBlurEnabled ?? this.isBlurEnabled,
        isFloatingNavigationBarEnabled:
            isFloatingNavigationBarEnabled ?? this.isFloatingNavigationBarEnabled,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'mode': mode.name,
        'seedColor': seedColor.name,
        'isBlurEnabled': isBlurEnabled,
        'isFloatingNavigationBarEnabled': isFloatingNavigationBarEnabled,
      };

  static T _enumByName<T extends Enum>(List<T> values, Object? raw, T fallback) {
    if (raw is! String) return fallback;
    for (final value in values) {
      if (value.name == raw) return value;
    }
    return fallback;
  }
}
