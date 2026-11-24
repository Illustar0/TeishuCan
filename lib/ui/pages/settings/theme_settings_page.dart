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
                child: PreferenceRow(
                  title: strings.settingsThemeMode,
                  trailing: ChevronTrailing(
                    valueText: strings.themeModeLabel(settings.mode),
                  ),
                  onTap: () => _pickThemeMode(context, settings),
                ),
              ),
              SectionTitle(strings.settingsThemeSeedColor),
              RoundedCard(
                margin: const EdgeInsets.fromLTRB(12, 8, 12, 0),
                padding: const EdgeInsets.all(16),
                child: Wrap(
                  spacing: 12,
                  runSpacing: 12,
                  children: [
                    for (final seed in SeedColor.values)
                      _SeedSwatch(
                        seed: seed,
                        label: strings.seedColorLabel(seed),
                        selected: settings.seedColor == seed,
                        onTap: () => preferences.updateTheme(
                          (s) => s.copyWith(seedColor: seed),
                        ),
                      ),
                  ],
                ),
              ),
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
  ) async {
    final palette = TeishuPalette.of(context);
    await showModalBottomSheet<void>(
      context: context,
      useSafeArea: true,
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              child: Text(
                strings.settingsThemeMode,
                style: Theme.of(ctx).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
              ),
            ),
            const SizedBox(height: 8),
            RoundedCard(
              color: palette.secondaryCard,
              margin: EdgeInsets.zero,
              radius: 16,
              child: Column(
                children: [
                  for (var i = 0; i < AppThemeMode.values.length; i++) ...[
                    if (i > 0) Divider(height: 1, color: palette.divider),
                    PreferenceRow(
                      title: strings.themeModeLabel(AppThemeMode.values[i]),
                      trailing: settings.mode == AppThemeMode.values[i]
                          ? Icon(
                              Icons.check_rounded,
                              color: Theme.of(ctx).colorScheme.primary,
                            )
                          : null,
                      onTap: () {
                        Navigator.of(ctx).pop();
                        preferences.updateTheme(
                          (s) => s.copyWith(mode: AppThemeMode.values[i]),
                        );
                      },
                    ),
                  ],
                ],
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
        borderRadius: BorderRadius.circular(24),
        child: Container(
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
      ),
    );
  }
}
