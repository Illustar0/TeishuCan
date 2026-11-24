import 'package:material_ui/material_ui.dart';

import '../../../data/preferences_controller.dart';
import '../../../domain/account.dart';
import '../../../domain/theme_settings.dart';
import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/frosted_bars.dart';
import '../../widgets/rounded_card.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({
    super.key,
    required this.strings,
    required this.student,
    required this.preferences,
    required this.isBlurEnabled,
    required this.bottomPadding,
    required this.onLoginClick,
    required this.onLogout,
    required this.onOpenThemeSettings,
    required this.onOpenElectricitySettings,
    required this.onOpenAbout,
  });

  final AppStrings strings;
  final StudentProfile? student;
  final PreferencesController preferences;
  final bool isBlurEnabled;
  final double bottomPadding;
  final VoidCallback onLoginClick;
  final Future<void> Function() onLogout;
  final VoidCallback onOpenThemeSettings;
  final VoidCallback onOpenElectricitySettings;
  final VoidCallback onOpenAbout;

  String _languageLabel(AppLanguage lang) => switch (lang) {
        AppLanguage.system => strings.languageSystem,
        AppLanguage.japanese => strings.languageJapanese,
        AppLanguage.chinese => strings.languageChinese,
      };

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);
    final topInset = MediaQuery.paddingOf(context).top + kToolbarHeight;

    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: FrostedTopBar(
        title: strings.destinationSettings,
        isBlurEnabled: isBlurEnabled,
      ),
      body: ListView(
        padding: EdgeInsets.only(
          top: topInset,
          bottom: bottomPadding + 16,
        ),
        children: [
          RoundedCard(
            child: student == null
                ? PreferenceRow(
                    leading: const Icon(Icons.account_circle_outlined),
                    title: strings.settingsLogin,
                    summary: strings.settingsLoginSummary,
                    trailing: const ChevronTrailing(),
                    onTap: onLoginClick,
                  )
                : PreferenceRow(
                    leading: const Icon(Icons.account_circle_outlined),
                    title: student!.name,
                    summary: student!.faculty,
                  ),
          ),
          RoundedCard(
            child: Column(
              children: [
                PreferenceRow(
                  leading: const Icon(Icons.palette_outlined),
                  title: strings.settingsTheme,
                  summary: strings.settingsThemeSummary,
                  trailing: const ChevronTrailing(),
                  onTap: onOpenThemeSettings,
                ),
                if (student != null) ...[
                  Divider(height: 1, color: palette.divider),
                  PreferenceRow(
                    leading: const Icon(Icons.bolt_outlined),
                    title: strings.settingsElectricity,
                    summary: strings.settingsElectricitySummary,
                    trailing: const ChevronTrailing(),
                    onTap: onOpenElectricitySettings,
                  ),
                ],
                Divider(height: 1, color: palette.divider),
                PreferenceRow(
                  leading: const Icon(Icons.translate_rounded),
                  title: strings.settingsLanguage,
                  trailing: ChevronTrailing(
                    valueText: _languageLabel(preferences.language),
                  ),
                  onTap: () => _pickLanguage(context),
                ),
              ],
            ),
          ),
          RoundedCard(
            child: PreferenceRow(
              leading: const Icon(Icons.info_outline_rounded),
              title: strings.settingsAbout,
              summary: strings.settingsAboutSummary,
              trailing: const ChevronTrailing(),
              onTap: onOpenAbout,
            ),
          ),
          if (student != null)
            RoundedCard(
              child: PreferenceRow(
                title: strings.settingsLogout,
                titleColor: theme.colorScheme.error,
                onTap: () => _confirmLogout(context),
              ),
            ),
        ],
      ),
    );
  }

  Future<void> _pickLanguage(BuildContext context) async {
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
                strings.settingsLanguage,
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
                  for (var i = 0; i < AppLanguage.values.length; i++) ...[
                    if (i > 0) Divider(height: 1, color: palette.divider),
                    PreferenceRow(
                      title: _languageLabel(AppLanguage.values[i]),
                      trailing: preferences.language == AppLanguage.values[i]
                          ? Icon(
                              Icons.check_rounded,
                              color: Theme.of(ctx).colorScheme.primary,
                            )
                          : null,
                      onTap: () {
                        Navigator.of(ctx).pop();
                        preferences.setLanguage(AppLanguage.values[i]);
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

  Future<void> _confirmLogout(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(strings.logoutTitle),
        content: Text(strings.logoutSummary),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: Text(strings.logoutCancel),
          ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            style: TextButton.styleFrom(
              foregroundColor: Theme.of(ctx).colorScheme.error,
            ),
            child: Text(strings.settingsLogout),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      try {
        await onLogout();
      } catch (_) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(strings.logoutFailed)),
          );
        }
      }
    }
  }
}
