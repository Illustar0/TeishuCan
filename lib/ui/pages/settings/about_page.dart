import 'package:material_ui/material_ui.dart';

import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/frosted_bars.dart';
import '../../widgets/rounded_card.dart';
import '../../widgets/teishucan_logo.dart';
import '../../widgets/url_launcher_helper.dart';
import 'open_source_licenses_page.dart';

class AboutPage extends StatelessWidget {
  const AboutPage({
    super.key,
    required this.strings,
    required this.isBlurEnabled,
  });

  final AppStrings strings;
  final bool isBlurEnabled;

  static const String _sourceCodeUrl = 'https://github.com/Illustar0/TeishuCan';
  static const String _licenseUrl = 'https://www.gnu.org/licenses/gpl-3.0.html';
  static const String _appVersion = '0.3.1 (2)';

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);
    final topInset = MediaQuery.paddingOf(context).top + kToolbarHeight;

    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: FrostedTopBar(
        title: strings.settingsAbout,
        isBlurEnabled: isBlurEnabled,
      ),
      body: ListView(
        padding: EdgeInsets.only(
          top: topInset + 16,
          bottom: MediaQuery.paddingOf(context).bottom + 24,
        ),
        children: [
          Column(
            children: [
              const TeishuCanLogo(size: 84),
              const SizedBox(height: 14),
              Text(
                strings.appName,
                style: theme.textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                _appVersion,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: palette.mutedText,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          SectionTitle(strings.settingsAbout),
          RoundedCard(
            margin: const EdgeInsets.fromLTRB(12, 8, 12, 0),
            child: Column(
              children: [
                PreferenceRow(
                  title: strings.settingsAboutSourceCode,
                  summary: strings.settingsAboutSourceCodeSummary,
                  trailing: const ChevronTrailing(),
                  onTap: () => openCampusUrl(_sourceCodeUrl),
                ),
                Divider(height: 1, color: palette.divider),
                PreferenceRow(
                  title: strings.settingsAboutLicense,
                  summary: strings.settingsAboutLicenseSummary,
                  trailing: const ChevronTrailing(),
                  onTap: () => openCampusUrl(_licenseUrl),
                ),
                Divider(height: 1, color: palette.divider),
                PreferenceRow(
                  title: strings.settingsAboutOpenSourceLicenses,
                  summary: strings.settingsAboutOpenSourceLicensesSummary,
                  trailing: const ChevronTrailing(),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => OpenSourceLicensesPage(
                        strings: strings,
                        isBlurEnabled: isBlurEnabled,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
