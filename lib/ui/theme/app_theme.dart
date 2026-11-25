import 'package:material_ui/material_ui.dart';

import '../../domain/theme_settings.dart';

@immutable
class TeishuPalette extends ThemeExtension<TeishuPalette> {
  const TeishuPalette({
    required this.background,
    required this.card,
    required this.secondaryCard,
    required this.divider,
    required this.mutedText,
    required this.frostedBar,
    required this.leadTint,
    required this.timetableCell,
    required this.timetableCellText,
  });

  final Color background;
  final Color card;
  final Color secondaryCard;
  final Color divider;
  final Color mutedText;
  final Color frostedBar;
  final Color leadTint;
  final Color timetableCell;
  final Color timetableCellText;

  static TeishuPalette of(BuildContext context) =>
      Theme.of(context).extension<TeishuPalette>()!;

  @override
  TeishuPalette copyWith({
    Color? background,
    Color? card,
    Color? secondaryCard,
    Color? divider,
    Color? mutedText,
    Color? frostedBar,
    Color? leadTint,
    Color? timetableCell,
    Color? timetableCellText,
  }) =>
      TeishuPalette(
        background: background ?? this.background,
        card: card ?? this.card,
        secondaryCard: secondaryCard ?? this.secondaryCard,
        divider: divider ?? this.divider,
        mutedText: mutedText ?? this.mutedText,
        frostedBar: frostedBar ?? this.frostedBar,
        leadTint: leadTint ?? this.leadTint,
        timetableCell: timetableCell ?? this.timetableCell,
        timetableCellText: timetableCellText ?? this.timetableCellText,
      );

  @override
  TeishuPalette lerp(covariant ThemeExtension<TeishuPalette>? other, double t) {
    if (other is! TeishuPalette) return this;
    return TeishuPalette(
      background: Color.lerp(background, other.background, t)!,
      card: Color.lerp(card, other.card, t)!,
      secondaryCard: Color.lerp(secondaryCard, other.secondaryCard, t)!,
      divider: Color.lerp(divider, other.divider, t)!,
      mutedText: Color.lerp(mutedText, other.mutedText, t)!,
      frostedBar: Color.lerp(frostedBar, other.frostedBar, t)!,
      leadTint: Color.lerp(leadTint, other.leadTint, t)!,
      timetableCell: Color.lerp(timetableCell, other.timetableCell, t)!,
      timetableCellText:
          Color.lerp(timetableCellText, other.timetableCellText, t)!,
    );
  }
}

DynamicSchemeVariant _schemeVariant(PaletteStyle style) => switch (style) {
      PaletteStyle.tonalSpot => DynamicSchemeVariant.tonalSpot,
      PaletteStyle.neutral => DynamicSchemeVariant.neutral,
      PaletteStyle.vibrant => DynamicSchemeVariant.vibrant,
      PaletteStyle.expressive => DynamicSchemeVariant.expressive,
      PaletteStyle.rainbow => DynamicSchemeVariant.rainbow,
      PaletteStyle.fruitSalad => DynamicSchemeVariant.fruitSalad,
      PaletteStyle.monochrome => DynamicSchemeVariant.monochrome,
      PaletteStyle.fidelity => DynamicSchemeVariant.fidelity,
      PaletteStyle.content => DynamicSchemeVariant.content,
    };

ThemeData buildTeishuTheme(ThemeSettings settings, Brightness brightness) {
  final isDark = brightness == Brightness.dark;
  final seed =
      settings.isMonetEnabled ? settings.seedColor.color : SeedColor.blue.color;
  final scheme = ColorScheme.fromSeed(
    seedColor: seed,
    brightness: brightness,
    dynamicSchemeVariant: settings.isMonetEnabled
        ? _schemeVariant(settings.paletteStyle)
        : DynamicSchemeVariant.tonalSpot,
  );

  final Color bg;
  final Color card;
  final Color secondaryCard;
  final Color divider;
  final Color mutedText;
  final Color frostedBar;

  if (settings.isMonetEnabled) {
    bg = isDark ? scheme.surface : scheme.surfaceContainerLowest;
    card = isDark ? scheme.surfaceContainerLow : scheme.surfaceContainer;
    secondaryCard =
        isDark ? scheme.surfaceContainerHigh : scheme.surfaceContainerHigh;
    divider = scheme.outlineVariant.withValues(alpha: 0.65);
    mutedText = scheme.onSurfaceVariant;
    frostedBar = (isDark ? scheme.surfaceContainer : scheme.surface)
        .withValues(alpha: 0.80);
  } else {
    bg = isDark ? const Color(0xFF000000) : const Color(0xFFF7F7F7);
    card = isDark ? const Color(0xFF1C1C1E) : const Color(0xFFFFFFFF);
    secondaryCard =
        isDark ? const Color(0xFF2C2C2E) : const Color(0xFFF2F2F7);
    divider = isDark ? const Color(0xFF2C2C2E) : const Color(0xFFE5E5EA);
    mutedText = isDark ? const Color(0xFF98989F) : const Color(0xFF6E6E73);
    frostedBar = isDark
        ? const Color(0xFF141416).withValues(alpha: 0.78)
        : Colors.white.withValues(alpha: 0.80);
  }

  final palette = TeishuPalette(
    background: bg,
    card: card,
    secondaryCard: secondaryCard,
    divider: divider,
    mutedText: mutedText,
    frostedBar: frostedBar,
    leadTint: scheme.primary.withValues(alpha: isDark ? 0.22 : 0.14),
    timetableCell: scheme.tertiaryContainer,
    timetableCellText: scheme.onTertiaryContainer,
  );

  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: bg,
    dividerColor: divider,
    appBarTheme: AppBarTheme(
      backgroundColor: Colors.transparent,
      foregroundColor: scheme.onSurface,
      elevation: 0,
      scrolledUnderElevation: 0,
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: card,
      surfaceTintColor: Colors.transparent,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: card,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
      ),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
      ),
    ),
    extensions: <ThemeExtension<dynamic>>[palette],
  );
}
