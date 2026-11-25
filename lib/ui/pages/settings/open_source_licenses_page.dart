import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';

import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/frosted_bars.dart';
import '../../widgets/rounded_card.dart';

class _PackageLicenseGroup {
  const _PackageLicenseGroup({
    required this.packageName,
    required this.paragraphs,
  });

  final String packageName;
  final List<String> paragraphs;
}

class OpenSourceLicensesPage extends StatefulWidget {
  const OpenSourceLicensesPage({
    super.key,
    required this.strings,
    required this.isBlurEnabled,
  });

  final AppStrings strings;
  final bool isBlurEnabled;

  @override
  State<OpenSourceLicensesPage> createState() => _OpenSourceLicensesPageState();
}

class _OpenSourceLicensesPageState extends State<OpenSourceLicensesPage> {
  late final Future<List<_PackageLicenseGroup>> _licensesFuture;

  @override
  void initState() {
    super.initState();
    _licensesFuture = _loadGroupedLicenses();
  }

  Future<List<_PackageLicenseGroup>> _loadGroupedLicenses() async {
    final grouped = <String, List<String>>{};
    await for (final entry in LicenseRegistry.licenses) {
      final text = entry.paragraphs
          .map((p) {
            final indent = p.indent >= 0 ? '  ' * p.indent : '';
            return '$indent${p.text}';
          })
          .join('\n\n')
          .trim();
      if (text.isEmpty) continue;
      for (final pkg in entry.packages) {
        grouped.putIfAbsent(pkg, () => <String>[]).add(text);
      }
    }
    final names = grouped.keys.toList()
      ..sort((a, b) => a.toLowerCase().compareTo(b.toLowerCase()));
    return names
        .map(
          (name) => _PackageLicenseGroup(
            packageName: name,
            paragraphs: grouped[name]!,
          ),
        )
        .toList(growable: false);
  }

  void _showPackageDialog(_PackageLicenseGroup group) {
    final strings = widget.strings;
    final palette = TeishuPalette.of(context);
    final fullText = group.paragraphs.join('\n\n────────────────────────\n\n');

    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(
          group.packageName,
          style: Theme.of(ctx).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w600,
              ),
        ),
        content: SizedBox(
          width: double.maxFinite,
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.sizeOf(ctx).height * 0.55,
            ),
            child: SingleChildScrollView(
              child: SelectableText(
                fullText,
                style: Theme.of(ctx).textTheme.bodySmall?.copyWith(
                      color: palette.mutedText,
                      height: 1.45,
                    ),
              ),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: Text(strings.licensesClose),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final strings = widget.strings;
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);
    final topInset = MediaQuery.paddingOf(context).top + kToolbarHeight;

    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: FrostedTopBar(
        title: strings.settingsAboutOpenSourceLicenses,
        isBlurEnabled: widget.isBlurEnabled,
      ),
      body: FutureBuilder<List<_PackageLicenseGroup>>(
        future: _licensesFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          final groups = snapshot.data ?? const <_PackageLicenseGroup>[];
          if (groups.isEmpty) {
            return Center(
              child: Text(
                strings.licensesEmpty,
                style: theme.textTheme.bodyLarge?.copyWith(
                  color: palette.mutedText,
                ),
              ),
            );
          }

          return ListView(
            padding: EdgeInsets.only(
              top: topInset + 4,
              bottom: MediaQuery.paddingOf(context).bottom + 24,
            ),
            children: [
              RoundedCard(
                child: Column(
                  children: [
                    for (var i = 0; i < groups.length; i++) ...[
                      if (i > 0) Divider(height: 1, color: palette.divider),
                      PreferenceRow(
                        title: groups[i].packageName,
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            DecoratedBox(
                              decoration: BoxDecoration(
                                color: theme.colorScheme.primary
                                    .withValues(alpha: 0.14),
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: Padding(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 8,
                                  vertical: 3,
                                ),
                                child: Text(
                                  strings.licensesCount(
                                    groups[i].paragraphs.length,
                                  ),
                                  style: theme.textTheme.labelSmall?.copyWith(
                                    color: theme.colorScheme.primary,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                            ),
                            const SizedBox(width: 4),
                            Icon(
                              Icons.chevron_right_rounded,
                              size: 20,
                              color: palette.mutedText,
                            ),
                          ],
                        ),
                        onTap: () => _showPackageDialog(groups[i]),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
