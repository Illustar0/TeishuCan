import 'dart:async';

import 'package:flutter/foundation.dart';

import '../domain/account.dart';
import 'campus_card_repository.dart';
import 'electricity_repository.dart';
import 'timetable_repository.dart';
import 'zzu_session.dart';

class CampusRefresher extends ChangeNotifier {
  CampusRefresher({
    required this._session,
    required this._timetable,
    required this._campusCard,
    required this._electricity,
  }) {
    _session.addListener(_onDependencyChanged);
    _timetable.addListener(_onDependencyChanged);
    _campusCard.addListener(_onDependencyChanged);
    _electricity.addListener(_onDependencyChanged);
  }

  final ZzuSessionController _session;
  final TimetableRepository _timetable;
  final CampusCardRepository _campusCard;
  final ElectricityRepository _electricity;

  Object? _pendingFailure;
  bool _manualRefreshing = false;
  bool _lastSignedIn = false;

  final Set<String> _autoInFlight = <String>{};

  Object? get refreshFailure => _pendingFailure;
  bool get isRefreshing =>
      _manualRefreshing ||
      _timetable.isRefreshing ||
      _session.isProfileRefreshing ||
      _campusCard.isRefreshing ||
      _electricity.isRefreshing;

  void acknowledgeFailure(Object failure) {
    if (identical(_pendingFailure, failure)) {
      _pendingFailure = null;
      notifyListeners();
    }
  }

  Future<void> refresh() async {
    if (_manualRefreshing) return;
    _manualRefreshing = true;
    notifyListeners();
    try {
      Object? firstFailure;
      final tasks = <Future<void> Function()>[
        _timetable.refresh,
        _session.refreshProfile,
        _campusCard.refresh,
        _electricity.refresh,
      ];
      await Future.wait<void>(
        tasks.map((task) async {
          try {
            await task();
          } catch (error) {
            firstFailure ??= error;
          }
        }),
      );
      if (firstFailure != null) {
        _pendingFailure = firstFailure;
      }
    } finally {
      _manualRefreshing = false;
      notifyListeners();
    }
  }

  void onForegroundResumed() {
    if (_session.account is! AccountSignedIn) return;
    refreshStale();
  }

  void refreshStale() {
    if (_session.account is! AccountSignedIn) return;
    final now = DateTime.now().toUtc();
    _triggerAutoRefresh(
      'campusCard',
      isStale: () => _campusCard.isStaleAt(now, const Duration(minutes: 5)),
      isBusy: () => _campusCard.isRefreshing,
      run: _campusCard.refresh,
    );
    _triggerAutoRefresh(
      'electricity',
      isStale: () => _electricity.isStaleAt(now, const Duration(minutes: 30)),
      isBusy: () => _electricity.isRefreshing,
      run: _electricity.refresh,
    );
    _triggerAutoRefresh(
      'timetable',
      isStale: () => _timetable.isStaleAt(now, const Duration(hours: 12)),
      isBusy: () => _timetable.isRefreshing,
      run: _timetable.refresh,
    );
    _triggerAutoRefresh(
      'profile',
      isStale: () => _session.isProfileStaleAt(now, const Duration(hours: 12)),
      isBusy: () => _session.isProfileRefreshing,
      run: _session.refreshProfile,
    );
  }

  void _triggerAutoRefresh(
    String key, {
    required bool Function() isStale,
    required bool Function() isBusy,
    required Future<void> Function() run,
  }) {
    if (_autoInFlight.contains(key) || isBusy() || !isStale()) return;
    _autoInFlight.add(key);
    unawaited(
      run().catchError((Object _) {
        // Silent background refresh: keep cached data on screen if offline.
      }).whenComplete(() {
        _autoInFlight.remove(key);
      }),
    );
  }

  void _onDependencyChanged() {
    final isSignedIn = _session.account is AccountSignedIn;
    if (isSignedIn && !_lastSignedIn) {
      _lastSignedIn = true;
      refreshStale();
    } else if (!isSignedIn) {
      _lastSignedIn = false;
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _session.removeListener(_onDependencyChanged);
    _timetable.removeListener(_onDependencyChanged);
    _campusCard.removeListener(_onDependencyChanged);
    _electricity.removeListener(_onDependencyChanged);
    super.dispose();
  }
}
