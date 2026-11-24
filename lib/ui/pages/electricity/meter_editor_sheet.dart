import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:uuid/uuid.dart';

import '../../../data/campus_card_repository.dart';
import '../../../data/electricity_repository.dart';
import '../../../domain/electricity.dart';
import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/rounded_card.dart';

enum _MeterSheetPage { detail, recharge, form, locationPicker }

Future<bool> showMeterEditorSheet({
  required BuildContext context,
  required AppStrings strings,
  required ElectricityRepository electricity,
  required CampusCardRepository campusCard,
  ElectricityMeter? meter,
}) async {
  final result = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => _MeterEditorSheet(
      strings: strings,
      electricity: electricity,
      campusCard: campusCard,
      meter: meter,
    ),
  );
  return result ?? false;
}

class _MeterEditorSheet extends StatefulWidget {
  const _MeterEditorSheet({
    required this.strings,
    required this.electricity,
    required this.campusCard,
    this.meter,
  });

  final AppStrings strings;
  final ElectricityRepository electricity;
  final CampusCardRepository campusCard;
  final ElectricityMeter? meter;

  @override
  State<_MeterEditorSheet> createState() => _MeterEditorSheetState();
}

class _MeterEditorSheetState extends State<_MeterEditorSheet> {
  late _MeterSheetPage _page;
  late final TextEditingController _nameController;
  final TextEditingController _amountController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();

  MeterLocation? _location;
  List<MeterOption> _levelPath = const <MeterOption>[];
  final Map<String, List<MeterOption>> _loadedOptions =
      <String, List<MeterOption>>{};
  final Set<String> _loadingPaths = <String>{};
  final Map<String, String?> _failedPaths = <String, String?>{};

  bool _isSubmittingRecharge = false;
  MeterRechargeResult? _rechargeOutcome;

  @override
  void initState() {
    super.initState();
    _page = widget.meter == null
        ? _MeterSheetPage.form
        : _MeterSheetPage.detail;
    _nameController = TextEditingController(text: widget.meter?.name ?? '');
    _location = widget.meter?.location;
  }

  @override
  void dispose() {
    _nameController.dispose();
    _amountController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  bool get _canSave =>
      _nameController.text.trim().isNotEmpty && _location != null;

  int? get _rechargeAmountYuan {
    final parsed = int.tryParse(_amountController.text.trim());
    return (parsed != null && parsed > 0) ? parsed : null;
  }

  bool get _canSubmitRecharge =>
      _rechargeAmountYuan != null &&
      _passwordController.text.length == 6 &&
      !_isSubmittingRecharge;

  _MeterSheetPage? get _previousPage => switch (_page) {
        _MeterSheetPage.detail => null,
        _MeterSheetPage.recharge => _MeterSheetPage.detail,
        _MeterSheetPage.form =>
          widget.meter == null ? null : _MeterSheetPage.detail,
        _MeterSheetPage.locationPicker => _MeterSheetPage.form,
      };

  String _title(AppStrings strings) => switch (_page) {
        _MeterSheetPage.detail => widget.meter?.name ?? '',
        _MeterSheetPage.recharge => strings.electricityRecharge,
        _MeterSheetPage.form => widget.meter == null
            ? strings.electricityMeterAdd
            : strings.electricityMeterEdit,
        _MeterSheetPage.locationPicker => strings.electricityMeterChooseLocation,
      };

  String _pathKey(List<MeterOption> path) =>
      path.map((o) => o.id).join('>');

  Future<void> _loadOptions(List<MeterOption> path, {bool force = false}) async {
    final key = _pathKey(path);
    if (!force &&
        (_loadedOptions.containsKey(key) || _loadingPaths.contains(key))) {
      return;
    }
    setState(() {
      _loadingPaths.add(key);
      _failedPaths.remove(key);
    });
    try {
      final options = await widget.electricity.meterOptions(path);
      if (!mounted) return;
      setState(() {
        _loadedOptions[key] = options;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _failedPaths[key] = e.toString();
      });
    } finally {
      if (mounted) {
        setState(() {
          _loadingPaths.remove(key);
        });
      }
    }
  }

  Future<void> _save() async {
    final loc = _location;
    if (!_canSave || loc == null) return;
    final meter = ElectricityMeter(
      id: widget.meter?.id ?? const Uuid().v4(),
      name: _nameController.text.trim(),
      location: loc,
    );
    Navigator.of(context).pop(false);
    await widget.electricity.saveMeter(meter);
    try {
      await widget.electricity.refresh();
    } catch (_) {
      // Page refresh handles user-visible error feedback.
    }
  }

  Future<void> _delete() async {
    final meter = widget.meter;
    if (meter == null) return;
    Navigator.of(context).pop(false);
    await widget.electricity.deleteMeter(meter);
  }

  Future<void> _submitRecharge() async {
    final meter = widget.meter;
    final amount = _rechargeAmountYuan;
    if (meter == null || amount == null || !_canSubmitRecharge) return;
    FocusScope.of(context).unfocus();
    final password = _passwordController.text;
    setState(() {
      _isSubmittingRecharge = true;
      _rechargeOutcome = null;
    });
    final outcome = await widget.electricity.recharge(
      meter: meter,
      amountYuan: amount,
      paymentPassword: password,
    );
    _passwordController.clear();
    if (!mounted) return;
    setState(() {
      _isSubmittingRecharge = false;
      _rechargeOutcome = outcome;
    });

    if (outcome is MeterRechargeSucceeded) {
      Navigator.of(context).pop(true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final strings = widget.strings;
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);
    final bottomInset = MediaQuery.viewInsetsOf(context).bottom;
    final prev = _previousPage;

    return PopScope(
      canPop: !_isSubmittingRecharge,
      child: Padding(
        padding: EdgeInsets.fromLTRB(20, 12, 20, 20 + bottomInset),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                IconButton(
                  onPressed: _isSubmittingRecharge
                      ? null
                      : () {
                          if (prev == null) {
                            Navigator.of(context).pop(false);
                          } else {
                            setState(() {
                              if (_page == _MeterSheetPage.recharge) {
                                _amountController.clear();
                                _passwordController.clear();
                                _rechargeOutcome = null;
                              }
                              _page = prev;
                            });
                          }
                        },
                  icon: Icon(
                    prev == null
                        ? Icons.close_rounded
                        : Icons.arrow_back_rounded,
                  ),
                ),
                Expanded(
                  child: Text(
                    _title(strings),
                    textAlign: TextAlign.center,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                if (_page == _MeterSheetPage.form)
                  IconButton(
                    onPressed: _canSave ? _save : null,
                    icon: const Icon(Icons.check_rounded),
                    tooltip: strings.electricityMeterSave,
                  )
                else
                  const SizedBox(width: 48),
              ],
            ),
            const SizedBox(height: 12),
            switch (_page) {
              _MeterSheetPage.detail => _buildDetail(strings, palette, theme),
              _MeterSheetPage.recharge =>
                _buildRecharge(strings, palette, theme),
              _MeterSheetPage.form => _buildForm(strings, palette, theme),
              _MeterSheetPage.locationPicker =>
                _buildLocationPicker(strings, palette, theme),
            },
          ],
        ),
      ),
    );
  }

  Widget _buildDetail(
    AppStrings strings,
    TeishuPalette palette,
    ThemeData theme,
  ) {
    final meter = widget.meter!;
    final reading = widget.electricity.meters
        .where((r) => r.meter.id == meter.id)
        .firstOrNull
        ?.kilowattHours;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        RoundedCard(
          color: palette.secondaryCard,
          margin: EdgeInsets.zero,
          radius: 16,
          child: Column(
            children: [
              PreferenceRow(
                title: strings.homeRemainingElectricity,
                trailing: Text(
                  reading == null
                      ? strings.homeNotFetched
                      : strings.electricityMeterKilowattHours(
                          formatKilowattHours(reading),
                        ),
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                    color: reading == null ? palette.mutedText : null,
                  ),
                ),
              ),
              Divider(height: 1, color: palette.divider),
              PreferenceRow(
                title: strings.electricityMeterLocation,
                summary: meter.location.path,
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        Row(
          children: [
            Expanded(
              child: FilledButton.tonal(
                onPressed: () => setState(() => _page = _MeterSheetPage.form),
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(46),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
                child: Text(strings.electricityMeterEditAction),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: FilledButton(
                onPressed: () => setState(() {
                  _rechargeOutcome = null;
                  _page = _MeterSheetPage.recharge;
                }),
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(46),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
                child: Text(strings.electricityRecharge),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildRecharge(
    AppStrings strings,
    TeishuPalette palette,
    ThemeData theme,
  ) {
    final balance = widget.campusCard.balanceYuan;
    String? errorText;
    switch (_rechargeOutcome) {
      case MeterRechargeRejected(reason: final reason):
        errorText = reason ?? strings.electricityRechargeFailed;
      case MeterRechargeUnconfirmed():
        errorText = strings.electricityRechargeUnconfirmed;
      case _:
        errorText = null;
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: _amountController,
          enabled: !_isSubmittingRecharge,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          onChanged: (_) => setState(() {}),
          decoration: InputDecoration(
            labelText: strings.electricityRechargeAmount,
            filled: true,
            fillColor: palette.secondaryCard,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: BorderSide.none,
            ),
          ),
        ),
        const SizedBox(height: 6),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: Text(
            strings.electricityRechargeBalance(
              balance != null ? formatYuan(balance) : strings.homeNotFetched,
            ),
            style: theme.textTheme.bodySmall?.copyWith(color: palette.mutedText),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _passwordController,
          enabled: !_isSubmittingRecharge,
          obscureText: true,
          keyboardType: TextInputType.number,
          inputFormatters: [
            FilteringTextInputFormatter.digitsOnly,
            LengthLimitingTextInputFormatter(6),
          ],
          onChanged: (_) => setState(() {}),
          decoration: InputDecoration(
            labelText: strings.electricityRechargePassword,
            filled: true,
            fillColor: palette.secondaryCard,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: BorderSide.none,
            ),
          ),
        ),
        if (errorText != null) ...[
          const SizedBox(height: 8),
          Text(
            errorText,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.error,
            ),
          ),
        ],
        const SizedBox(height: 14),
        FilledButton(
          onPressed: _canSubmitRecharge ? _submitRecharge : null,
          style: FilledButton.styleFrom(
            minimumSize: const Size.fromHeight(48),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(14),
            ),
          ),
          child: _isSubmittingRecharge
              ? Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                    const SizedBox(width: 10),
                    Text(strings.electricityRechargeSubmitting),
                  ],
                )
              : Text(strings.electricityRechargeConfirm),
        ),
      ],
    );
  }

  Widget _buildForm(
    AppStrings strings,
    TeishuPalette palette,
    ThemeData theme,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: _nameController,
          onChanged: (_) => setState(() {}),
          decoration: InputDecoration(
            labelText: strings.electricityMeterName,
            filled: true,
            fillColor: palette.secondaryCard,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: BorderSide.none,
            ),
          ),
        ),
        const SizedBox(height: 12),
        RoundedCard(
          color: palette.secondaryCard,
          margin: EdgeInsets.zero,
          radius: 16,
          child: PreferenceRow(
            title: strings.electricityMeterLocation,
            summary: _location?.path ?? strings.electricityMeterNotSelected,
            trailing: const ChevronTrailing(),
            onTap: () {
              FocusScope.of(context).unfocus();
              setState(() {
                _levelPath = const <MeterOption>[];
                _page = _MeterSheetPage.locationPicker;
              });
              _loadOptions(const <MeterOption>[]);
            },
          ),
        ),
        if (widget.meter != null) ...[
          const SizedBox(height: 14),
          TextButton(
            onPressed: _delete,
            style: TextButton.styleFrom(
              foregroundColor: theme.colorScheme.error,
              minimumSize: const Size.fromHeight(44),
            ),
            child: Text(strings.electricityMeterDelete),
          ),
        ],
      ],
    );
  }

  Widget _buildLocationPicker(
    AppStrings strings,
    TeishuPalette palette,
    ThemeData theme,
  ) {
    final levels = strings.meterLevelTitles;
    final key = _pathKey(_levelPath);
    final isLoading = _loadingPaths.contains(key);
    final failed = _failedPaths[key];
    final options = _loadedOptions[key];
    final listHeight = MediaQuery.sizeOf(context).height * 0.44;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              for (var i = 0; i <= _levelPath.length; i++) ...[
                if (i > 0)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: Icon(
                      Icons.chevron_right_rounded,
                      size: 16,
                      color: palette.mutedText,
                    ),
                  ),
                ActionChip(
                  label: Text(
                    i < _levelPath.length
                        ? _levelPath[i].name
                        : levels[_levelPath.length],
                  ),
                  onPressed: i < _levelPath.length
                      ? () {
                          final nextPath = _levelPath.sublist(0, i);
                          setState(() => _levelPath = nextPath);
                          _loadOptions(nextPath);
                        }
                      : null,
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 8),
        SizedBox(
          height: listHeight,
          child: isLoading && options == null
              ? const Center(child: CircularProgressIndicator())
              : failed != null && options == null
                  ? Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            strings.meterOptionsFailed,
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: palette.mutedText,
                            ),
                          ),
                          const SizedBox(height: 8),
                          FilledButton.tonal(
                            onPressed: () =>
                                _loadOptions(_levelPath, force: true),
                            child: Text(strings.meterOptionsRetry),
                          ),
                        ],
                      ),
                    )
                  : (options == null || options.isEmpty)
                      ? Center(
                          child: Text(
                            strings.meterOptionsEmpty,
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: palette.mutedText,
                            ),
                          ),
                        )
                      : RoundedCard(
                          color: palette.secondaryCard,
                          margin: EdgeInsets.zero,
                          radius: 16,
                          child: ListView.separated(
                            itemCount: options.length,
                            separatorBuilder: (_, _) =>
                                Divider(height: 1, color: palette.divider),
                            itemBuilder: (_, index) {
                              final option = options[index];
                              return PreferenceRow(
                                title: option.name,
                                trailing: const ChevronTrailing(),
                                onTap: () {
                                  final nextPath = <MeterOption>[
                                    ..._levelPath,
                                    option,
                                  ];
                                  if (nextPath.length < levels.length) {
                                    setState(() => _levelPath = nextPath);
                                    _loadOptions(nextPath);
                                  } else {
                                    setState(() {
                                      _location = MeterLocation(
                                        meterId: option.id,
                                        path: nextPath
                                            .map((o) => o.name)
                                            .join(' / '),
                                      );
                                      _page = _MeterSheetPage.form;
                                    });
                                  }
                                },
                              );
                            },
                          ),
                        ),
        ),
      ],
    );
  }
}
