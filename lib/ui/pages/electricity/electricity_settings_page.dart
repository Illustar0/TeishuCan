import 'package:material_ui/material_ui.dart';

import '../../../data/campus_card_repository.dart';
import '../../../data/electricity_repository.dart';
import '../../../domain/campus_exception.dart';
import '../../../domain/electricity.dart';
import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/frosted_bars.dart';
import '../../widgets/rounded_card.dart';
import 'meter_editor_sheet.dart';

class ElectricitySettingsPage extends StatefulWidget {
  const ElectricitySettingsPage({
    super.key,
    required this.strings,
    required this.electricity,
    required this.campusCard,
    required this.isBlurEnabled,
  });

  final AppStrings strings;
  final ElectricityRepository electricity;
  final CampusCardRepository campusCard;
  final bool isBlurEnabled;

  @override
  State<ElectricitySettingsPage> createState() =>
      _ElectricitySettingsPageState();
}

class _ElectricitySettingsPageState extends State<ElectricitySettingsPage> {
  Future<void> _refresh() async {
    try {
      if (widget.electricity.meters.isEmpty) return;
      await Future.wait(<Future<void>>[
        widget.electricity.refresh(),
        widget.campusCard.refresh(),
      ]);
    } on InvalidMeterLocationException {
      _showMessage(widget.strings.electricityMeterReselectLocation);
    } on CampusRequestException catch (e) {
      _showMessage(e.reason ?? widget.strings.electricityRefreshFailed);
    } catch (_) {
      _showMessage(widget.strings.electricityRefreshFailed);
    }
  }

  Future<void> _openEditor(ElectricityMeter? meter) async {
    final recharged = await showMeterEditorSheet(
      context: context,
      strings: widget.strings,
      electricity: widget.electricity,
      campusCard: widget.campusCard,
      meter: meter,
    );
    if (recharged && mounted) {
      _showMessage(widget.strings.electricityRechargeSuccess);
      await _refresh();
    }
  }

  void _showMessage(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  @override
  Widget build(BuildContext context) {
    final strings = widget.strings;
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);
    final topInset = MediaQuery.paddingOf(context).top + kToolbarHeight;

    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[
        widget.electricity,
        widget.campusCard,
      ]),
      builder: (context, _) {
        final meters = widget.electricity.meters;
        return Scaffold(
          extendBodyBehindAppBar: true,
          appBar: FrostedTopBar(
            title: strings.settingsElectricity,
            isBlurEnabled: widget.isBlurEnabled,
            actions: [
              IconButton(
                onPressed: () => _openEditor(null),
                tooltip: strings.electricityMeterAdd,
                icon: const Icon(Icons.add_rounded),
              ),
            ],
          ),
          body: RefreshIndicator(
            edgeOffset: topInset,
            onRefresh: _refresh,
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: EdgeInsets.only(
                top: topInset,
                bottom: MediaQuery.paddingOf(context).bottom + 16,
              ),
              children: [
                if (meters.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 24,
                      vertical: 56,
                    ),
                    child: Column(
                      children: [
                        Text(
                          strings.electricityMeterEmpty,
                          style: theme.textTheme.bodyLarge?.copyWith(
                            color: palette.mutedText,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          strings.electricityMeterEmptyHint,
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: palette.mutedText,
                          ),
                        ),
                      ],
                    ),
                  )
                else
                  RoundedCard(
                    child: Column(
                      children: [
                        for (var i = 0; i < meters.length; i++) ...[
                          if (i > 0) Divider(height: 1, color: palette.divider),
                          PreferenceRow(
                            title: meters[i].meter.name,
                            summary: meters[i].meter.location.path,
                            trailing: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                if (meters[i].kilowattHours == null)
                                  Text(
                                    strings.homeNotFetched,
                                    style: theme.textTheme.bodySmall?.copyWith(
                                      color: palette.mutedText,
                                    ),
                                  )
                                else
                                  Text(
                                    strings.electricityMeterKilowattHours(
                                      formatKilowattHours(
                                        meters[i].kilowattHours!,
                                      ),
                                    ),
                                    style: theme.textTheme.titleMedium?.copyWith(
                                      fontWeight: FontWeight.w600,
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
                            onTap: () => _openEditor(meters[i].meter),
                          ),
                        ],
                      ],
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}
