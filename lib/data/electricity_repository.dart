import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:libzzu_ffi/libzzu_ffi.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/campus_exception.dart';
import '../domain/electricity.dart';
import 'snapshot_cache.dart';
import 'zzu_session.dart';

class ElectricityRepository extends ChangeNotifier {
  ElectricityRepository({
    required this._prefs,
    required this._session,
    required SnapshotStorage snapshots,
  }) : _remainingEnergy = SnapshotCache<Map<String, double>>(
          storage: snapshots,
          fileName: 'electricity.json',
          initialValue: const <String, double>{},
          fromJson: (json) {
            final map = json as Map<String, dynamic>? ?? const {};
            return map.map(
              (k, v) => MapEntry(k, (v as num).toDouble()),
            );
          },
          toJson: (value) => value,
        ) {
    _loadSavedMeters();
    _remainingEnergy.addListener(notifyListeners);
    _session.addSessionBinding(_remainingEnergy.bindSession);
  }

  static const String _metersKey = 'teishucan.electricity_meters.v1';

  final SharedPreferences _prefs;
  final ZzuSessionController _session;
  final SnapshotCache<Map<String, double>> _remainingEnergy;

  List<ElectricityMeter> _savedMeters = const <ElectricityMeter>[];
  bool _isRefreshing = false;

  List<MeterReading> get meters => _savedMeters
      .map(
        (m) => MeterReading(
          meter: m,
          kilowattHours: _remainingEnergy.value[m.location.meterId],
        ),
      )
      .toList(growable: false);

  bool get isRefreshing => _isRefreshing;

  void _loadSavedMeters() {
    final raw = _prefs.getString(_metersKey);
    if (raw == null || raw.isEmpty) return;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List<dynamic>) {
        _savedMeters = decoded
            .map((e) => ElectricityMeter.fromJson(e as Map<String, dynamic>))
            .toList(growable: false);
      }
    } catch (_) {
      _savedMeters = const <ElectricityMeter>[];
    }
  }

  Future<void> _persistSavedMeters() => _prefs.setString(
        _metersKey,
        jsonEncode(_savedMeters.map((m) => m.toJson()).toList(growable: false)),
      );

  bool isStaleAt(DateTime now, Duration maxAge) {
    final roomIds = _savedMeters
        .map((m) => m.location.meterId)
        .where((id) => _tryParseRoomId(id) != null)
        .toList(growable: false);
    if (roomIds.isEmpty) return false;
    return _remainingEnergy.isStaleAt(
      now,
      maxAge,
      isComplete: (energy) => roomIds.every(energy.containsKey),
    );
  }

  Future<void> saveMeter(ElectricityMeter meter) async {
    final exists = _savedMeters.any((m) => m.id == meter.id);
    _savedMeters = exists
        ? _savedMeters
            .map((m) => m.id == meter.id ? meter : m)
            .toList(growable: false)
        : <ElectricityMeter>[..._savedMeters, meter];
    notifyListeners();
    await _persistSavedMeters();
  }

  Future<void> deleteMeter(ElectricityMeter meter) async {
    _savedMeters = _savedMeters
        .where((m) => m.id != meter.id)
        .toList(growable: false);
    notifyListeners();
    await _persistSavedMeters();
  }

  Future<List<MeterOption>> meterOptions(List<MeterOption> path) =>
      _session.request(CampusFeature.electricity, (clients, _) async {
        final card = await clients.ecard();
        final list = await card.locations(locationParent(path));
        return list
            .map((item) => MeterOption(id: item.id, name: item.name))
            .toList(growable: false);
      });

  Future<MeterRechargeResult> recharge({
    required ElectricityMeter meter,
    required int amountYuan,
    required String paymentPassword,
  }) async {
    if (amountYuan <= 0) {
      throw ArgumentError.value(amountYuan, 'amountYuan', 'Must be positive');
    }
    try {
      await _session.request(CampusFeature.electricity, (clients, _) async {
        final room = parseRoomId(meter.location.meterId);
        final card = await clients.ecard();
        await card.rechargeEnergy(
          paymentPassword: paymentPassword,
          amount: amountYuan,
          room: room,
        );
      });
      return const MeterRechargeSucceeded();
    } on CampusRequestException catch (cause) {
      return classifyRechargeFailure(cause);
    }
  }

  Future<void> refresh() async {
    _isRefreshing = true;
    notifyListeners();
    try {
      final refreshVersion = _remainingEnergy.nextRefreshVersion();
      final roomIds = _savedMeters.map((m) => m.location.meterId).toSet();
      final validRooms = <String, String>{};
      for (final id in roomIds) {
        final parsed = _tryParseRoomId(id);
        if (parsed != null) {
          validRooms[id] = parsed;
        }
      }
      if (validRooms.isNotEmpty) {
        await _session.request(
          CampusFeature.electricity,
          (clients, signInId) async {
            final card = await clients.ecard();
            final pairs = await Future.wait<MapEntry<String, double>>(
              validRooms.entries.map((entry) async {
                final kwh = await card.remainingEnergy(room: entry.value);
                return MapEntry<String, double>(entry.key, kwh);
              }),
            );
            final energy = Map<String, double>.fromEntries(pairs);
            await _remainingEnergy.update(
              signInId,
              refreshVersion,
              (_) => energy,
            );
          },
        );
      }
      if (validRooms.length < roomIds.length) {
        throw const InvalidMeterLocationException();
      }
    } finally {
      _isRefreshing = false;
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _remainingEnergy.removeListener(notifyListeners);
    _remainingEnergy.dispose();
    super.dispose();
  }
}

String? _tryParseRoomId(String meterId) {
  try {
    return parseRoomId(meterId);
  } on ZzuInvalidInputException {
    return null;
  }
}

MeterRechargeResult classifyRechargeFailure(CampusRequestException exception) {
  final inner = exception.cause;
  return switch (inner) {
    ZzuRemoteException() ||
    ZzuAuthenticationException() ||
    ZzuInvalidInputException() ||
    StateError() =>
      MeterRechargeRejected(exception.reason),
    _ => const MeterRechargeUnconfirmed(),
  };
}
