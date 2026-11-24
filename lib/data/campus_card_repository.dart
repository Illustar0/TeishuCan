import 'package:flutter/foundation.dart';
import 'package:libzzu_ffi/libzzu_ffi.dart';

import '../domain/campus_exception.dart';
import '../domain/electricity.dart';
import 'snapshot_cache.dart';
import 'zzu_session.dart';

class CampusCardRepository extends ChangeNotifier {
  CampusCardRepository({
    required this._session,
    required SnapshotStorage snapshots,
  }) : _balance = SnapshotCache<String?>(
          storage: snapshots,
          fileName: 'campus_card.json',
          initialValue: null,
          fromJson: (json) => json as String?,
          toJson: (value) => value,
        ) {
    _balance.addListener(notifyListeners);
    _session.addSessionBinding(_balance.bindSession);
  }

  final ZzuSessionController _session;
  final SnapshotCache<String?> _balance;
  bool _isRefreshing = false;

  String? get balanceYuan => _balance.value;
  bool get isRefreshing => _isRefreshing;

  bool isStaleAt(DateTime now, Duration maxAge) =>
      _balance.isStaleAt(now, maxAge);

  void expire() {
    _balance.expire();
  }

  Future<void> refresh() async {
    _isRefreshing = true;
    notifyListeners();
    try {
      final refreshVersion = _balance.nextRefreshVersion();
      await _session.request(CampusFeature.campusCard, (clients, signInId) async {
        final card = await clients.ecard();
        final amount = await card.balance();
        await _balance.update(signInId, refreshVersion, (_) => amount);
      });
    } finally {
      _isRefreshing = false;
      notifyListeners();
    }
  }

  Future<CampusCardRechargeOrder> createRechargeOrder(int amountYuan) {
    if (amountYuan <= 0) {
      throw ArgumentError.value(amountYuan, 'amountYuan', 'Must be positive');
    }
    return _session.request(CampusFeature.campusCard, (clients, _) async {
      final card = await clients.ecard();
      final order = await card.createRechargeOrder(amountYuan);
      final channels = await card.paymentChannels(order);
      return CampusCardRechargeOrder(
        amountYuan: order.amount,
        channels: channels
            .map(
              (c) => PaymentChannelInfo(
                name: c.name,
                checkoutUrl: rechargeOrderCheckoutUrlFor(order, c),
              ),
            )
            .toList(growable: false),
      );
    });
  }

  @override
  void dispose() {
    _balance.removeListener(notifyListeners);
    _balance.dispose();
    super.dispose();
  }
}
