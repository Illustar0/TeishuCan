import 'package:material_ui/material_ui.dart';

import '../../../data/preferences_controller.dart';
import '../../../domain/theme_settings.dart';
import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/frosted_bars.dart';
import '../../widgets/rounded_card.dart';

class ThemeSettingsPage extends StatelessWidget {
  const ThemeSettingsPage({
    super.key,
    required this.strings,
    required this.preferences,
  });

  final AppStrings strings;
  final PreferencesController preferences;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);

    return ListenableBuilder(
      listenable: preferences,
      builder: (context, _) {
        final settings = preferences.theme;
        final topInset = MediaQuery.paddingOf(context).top + kToolbarHeight;

        return Scaffold(
          extendBodyBehindAppBar: true,
          appBar: FrostedTopBar(
            title: strings.settingsTheme,
            isBlurEnabled: settings.isBlurEnabled,
          ),
          body: ListView(
            padding: EdgeInsets.only(
              top: topInset,
              bottom: MediaQuery.paddingOf(context).bottom + 16,
            ),
            children: [
              RoundedCard(
                child: Column(
                  children: [
                    PreferenceRow(
                      title: strings.settingsThemeMode,
                      trailing: ChevronTrailing(
                        valueText: strings.themeModeLabel(settings.mode),
                      ),
                      onTap: () => _pickThemeMode(context, settings),
                    ),
                    Divider(height: 1, color: palette.divider),
                    PreferenceRow(
                      title: strings.settingsThemeMonet,
                      summary: strings.settingsThemeMonetSummary,
                      trailing: Switch(
                        value: settings.isMonetEnabled,
                        onChanged: (value) => preferences.updateTheme(
                          (s) => s.copyWith(isMonetEnabled: value),
                        ),
                      ),
                      onTap: () => preferences.updateTheme(
                        (s) => s.copyWith(isMonetEnabled: !s.isMonetEnabled),
                      ),
                    ),
                    if (settings.isMonetEnabled) ...[
                      Divider(height: 1, color: palette.divider),
                      PreferenceRow(
                        title: strings.settingsThemePaletteStyle,
                        trailing: ChevronTrailing(
                          valueText:
                              strings.paletteStyleLabel(settings.paletteStyle),
                        ),
                        onTap: () => _pickPaletteStyle(context, settings),
                      ),
                    ],
                  ],
                ),
              ),
              if (settings.isMonetEnabled) ...[
                SectionTitle(strings.settingsThemeSeedColor),
                RoundedCard(
                  margin: const EdgeInsets.fromLTRB(12, 8, 12, 0),
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    children: [
                      for (var row = 0;
                          row < SeedColor.values.length;
                          row += 4) ...[
                        if (row > 0) const SizedBox(height: 16),
                        Row(
                          children: [
                            for (var i = row;
                                i < row + 4 && i < SeedColor.values.length;
                                i++)
                              Expanded(
                                child: _SeedSwatch(
                                  seed: SeedColor.values[i],
                                  label:
                                      strings.seedColorLabel(SeedColor.values[i]),
                                  selected:
                                      settings.seedColor == SeedColor.values[i],
                                  onTap: () => preferences.updateTheme(
                                    (s) => s.copyWith(
                                      seedColor: SeedColor.values[i],
                                    ),
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
              ],
              RoundedCard(
                child: Column(
                  children: [
                    PreferenceRow(
                      title: strings.settingsThemeBlur,
                      summary: strings.settingsThemeBlurSummary,
                      trailing: Switch(
                        value: settings.isBlurEnabled,
                        onChanged: (value) => preferences.updateTheme(
                          (s) => s.copyWith(isBlurEnabled: value),
                        ),
                      ),
                      onTap: () => preferences.updateTheme(
                        (s) => s.copyWith(isBlurEnabled: !s.isBlurEnabled),
                      ),
                    ),
                    Divider(height: 1, color: palette.divider),
                    PreferenceRow(
                      title: strings.settingsThemeFloatingNavBar,
                      summary: strings.settingsThemeFloatingNavBarSummary,
                      trailing: Switch(
                        value: settings.isFloatingNavigationBarEnabled,
                        onChanged: (value) => preferences.updateTheme(
                          (s) =>
                              s.copyWith(isFloatingNavigationBarEnabled: value),
                        ),
                      ),
                      onTap: () => preferences.updateTheme(
                        (s) => s.copyWith(
                          isFloatingNavigationBarEnabled:
                              !s.isFloatingNavigationBarEnabled,
                        ),
                      ),
                    ),
                    Divider(height: 1, color: palette.divider),
                    PreferenceRow(
                      title: strings.settingsThemeLiquidGlass,
                      summary: strings.settingsThemeLiquidGlassSummary,
                      trailing: Switch(
                        value: settings.isLiquidGlassEnabled,
                        onChanged: (value) => preferences.updateTheme(
                          (s) => s.copyWith(isLiquidGlassEnabled: value),
                        ),
                      ),
                      onTap: () => preferences.updateTheme(
                        (s) => s.copyWith(
                          isLiquidGlassEnabled: !s.isLiquidGlassEnabled,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _pickThemeMode(
    BuildContext context,
    ThemeSettings settings,
  ) =>
      _pickOption<AppThemeMode>(
        context: context,
        title: strings.settingsThemeMode,
        options: AppThemeMode.values,
        selected: settings.mode,
        labelOf: strings.themeModeLabel,
        onSelect: (mode) => preferences.updateTheme((s) => s.copyWith(mode: mode)),
      );

  Future<void> _pickPaletteStyle(
    BuildContext context,
    ThemeSettings settings,
  ) =>
      _pickOption<PaletteStyle>(
        context: context,
        title: strings.settingsThemePaletteStyle,
        options: PaletteStyle.values,
        selected: settings.paletteStyle,
        labelOf: strings.paletteStyleLabel,
        onSelect: (style) =>
            preferences.updateTheme((s) => s.copyWith(paletteStyle: style)),
      );

  Future<void> _pickOption<T>({
    required BuildContext context,
    required String title,
    required List<T> options,
    required T selected,
    required String Function(T option) labelOf,
    required ValueChanged<T> onSelect,
  }) async {
    final palette = TeishuPalette.of(context);
    await showModalBottomSheet<void>(
      context: context,
      useSafeArea: true,
      isScrollControlled: true,
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              child: Text(
                title,
                style: Theme.of(ctx).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
              ),
            ),
            const SizedBox(height: 8),
            ConstrainedBox(
              constraints: BoxConstraints(
                maxHeight: MediaQuery.sizeOf(ctx).height * 0.55,
              ),
              child: RoundedCard(
                color: palette.secondaryCard,
                margin: EdgeInsets.zero,
                radius: 16,
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      for (var i = 0; i < options.length; i++) ...[
                        if (i > 0) Divider(height: 1, color: palette.divider),
                        PreferenceRow(
                          title: labelOf(options[i]),
                          trailing: selected == options[i]
                              ? Icon(
                                  Icons.check_rounded,
                                  color: Theme.of(ctx).colorScheme.primary,
                                )
                              : null,
                          onTap: () {
                            Navigator.of(ctx).pop();
                            onSelect(options[i]);
                          },
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SeedSwatch extends StatelessWidget {
  const _SeedSwatch({
    required this.seed,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final SeedColor seed;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final checkColor =
        seed.color.computeLuminance() > 0.5 ? Colors.black : Colors.white;
    return Semantics(
      label: label,
      selected: selected,
      button: true,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: seed.color,
                  shape: BoxShape.circle,
                  border: selected
                      ? Border.all(
                          color: Theme.of(context).colorScheme.onSurface,
                          width: 2.5,
                        )
                      : null,
                ),
                child: selected
                    ? Icon(Icons.check_rounded, color: checkColor, size: 22)
                    : null,
              ),
              const SizedBox(height: 6),
              Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelSmall,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
