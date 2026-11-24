import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';

import '../../../data/campus_card_repository.dart';
import '../../../domain/campus_exception.dart';
import '../../../domain/electricity.dart';
import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/rounded_card.dart';
import '../../widgets/url_launcher_helper.dart';

Future<void> showCampusCardRechargeSheet({
  required BuildContext context,
  required AppStrings strings,
  required CampusCardRepository campusCard,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => _CampusCardRechargeSheet(
      strings: strings,
      campusCard: campusCard,
    ),
  );
}

class _CampusCardRechargeSheet extends StatefulWidget {
  const _CampusCardRechargeSheet({
    required this.strings,
    required this.campusCard,
  });

  final AppStrings strings;
  final CampusCardRepository campusCard;

  @override
  State<_CampusCardRechargeSheet> createState() =>
      _CampusCardRechargeSheetState();
}

class _CampusCardRechargeSheetState extends State<_CampusCardRechargeSheet> {
  final TextEditingController _amountController = TextEditingController();
  CampusCardRechargeOrder? _order;
  bool _isSubmitting = false;
  bool _hasFailed = false;
  String? _failureReason;

  int? get _amountYuan {
    final parsed = int.tryParse(_amountController.text.trim());
    return (parsed != null && parsed > 0) ? parsed : null;
  }

  @override
  void dispose() {
    _amountController.dispose();
    super.dispose();
  }

  Future<void> _submitAmount() async {
    final amount = _amountYuan;
    if (amount == null || _isSubmitting) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _isSubmitting = true;
      _hasFailed = false;
      _failureReason = null;
    });
    try {
      final order = await widget.campusCard.createRechargeOrder(amount);
      if (!mounted) return;
      setState(() {
        _order = order;
      });
    } on CampusRequestException catch (e) {
      if (!mounted) return;
      setState(() {
        _hasFailed = true;
        _failureReason = e.reason;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _hasFailed = true;
        _failureReason = null;
      });
    } finally {
      if (mounted) {
        setState(() {
          _isSubmitting = false;
        });
      }
    }
  }

  Future<void> _selectChannel(PaymentChannelInfo channel) async {
    final opened = await openCampusUrl(channel.checkoutUrl);
    if (!mounted) return;
    if (opened) {
      widget.campusCard.expire();
      Navigator.of(context).pop();
    } else {
      setState(() {
        _hasFailed = true;
        _failureReason = null;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final strings = widget.strings;
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);
    final bottomInset = MediaQuery.viewInsetsOf(context).bottom;
    final balance = widget.campusCard.balanceYuan;

    return PopScope(
      canPop: !_isSubmitting,
      child: Padding(
        padding: EdgeInsets.fromLTRB(20, 12, 20, 20 + bottomInset),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                IconButton(
                  onPressed: _isSubmitting
                      ? null
                      : () {
                          if (_order != null) {
                            setState(() {
                              _order = null;
                              _hasFailed = false;
                              _failureReason = null;
                            });
                          } else {
                            Navigator.of(context).pop();
                          }
                        },
                  icon: Icon(
                    _order == null
                        ? Icons.close_rounded
                        : Icons.arrow_back_rounded,
                  ),
                ),
                Expanded(
                  child: Text(
                    _order == null
                        ? strings.electricityRecharge
                        : strings.campusCardRechargeChannelTitle,
                    textAlign: TextAlign.center,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                const SizedBox(width: 48),
              ],
            ),
            const SizedBox(height: 12),
            if (_order == null) ...[
              TextField(
                controller: _amountController,
                enabled: !_isSubmitting,
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
              const SizedBox(height: 8),
              Text(
                strings.electricityRechargeBalance(
                  balance != null ? formatYuan(balance) : strings.homeNotFetched,
                ),
                style: theme.textTheme.bodySmall?.copyWith(
                  color: palette.mutedText,
                ),
              ),
              if (_hasFailed) ...[
                const SizedBox(height: 6),
                Text(
                  _failureReason ?? strings.campusCardRechargeFailed,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.error,
                  ),
                ),
              ],
              const SizedBox(height: 6),
              Text(
                strings.campusCardRechargeNotice,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: palette.mutedText,
                ),
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed:
                    _amountYuan != null && !_isSubmitting ? _submitAmount : null,
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(48),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
                child: _isSubmitting
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
                    : Text(strings.campusCardRechargeNext),
              ),
            ] else ...[
              RoundedCard(
                color: palette.secondaryCard,
                margin: EdgeInsets.zero,
                radius: 16,
                child: PreferenceRow(
                  title: strings.campusCardRechargeAmount,
                  trailing: Text(
                    formatYuan(_order!.amountYuan.toString()),
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              RoundedCard(
                color: palette.secondaryCard,
                margin: EdgeInsets.zero,
                radius: 16,
                child: Column(
                  children: [
                    for (var i = 0; i < _order!.channels.length; i++) ...[
                      if (i > 0) Divider(height: 1, color: palette.divider),
                      PreferenceRow(
                        title: _order!.channels[i].name,
                        trailing: const ChevronTrailing(),
                        onTap: () => _selectChannel(_order!.channels[i]),
                      ),
                    ],
                  ],
                ),
              ),
              if (_hasFailed) ...[
                const SizedBox(height: 8),
                Text(
                  strings.campusCardRechargeFailed,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.error,
                  ),
                ),
              ],
            ],
          ],
        ),
      ),
    );
  }
}
