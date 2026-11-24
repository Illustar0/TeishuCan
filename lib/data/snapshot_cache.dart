import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SnapshotStorage {
  SnapshotStorage(this._prefs);

  static const String _prefix = 'teishucan.snapshot.';

  final SharedPreferences _prefs;

  String? readRaw(String key) => _prefs.getString('$_prefix$key');

  Future<void> writeRaw(String key, String value) =>
      _prefs.setString('$_prefix$key', value);

  Future<void> clearAll() async {
    final keys = _prefs
        .getKeys()
        .where((k) => k.startsWith(_prefix))
        .toList(growable: false);
    for (final key in keys) {
      await _prefs.remove(key);
    }
  }
}

class SnapshotCache<T> extends ChangeNotifier {
  SnapshotCache({
    required this._storage,
    required this.fileName,
    required this.initialValue,
    required this._fromJson,
    required this._toJson,
  }) : _value = initialValue;

  final SnapshotStorage _storage;
  final String fileName;
  final T initialValue;
  final T Function(Object? json) _fromJson;
  final Object? Function(T value) _toJson;

  T _value;
  String? _activeSignInId;
  DateTime? _fetchedAt;
  bool _isExpired = false;
  int _refreshVersion = 0;

  T get value => _value;

  int nextRefreshVersion() => ++_refreshVersion;

  void bindSession(String? signInId) {
    if (_activeSignInId == signInId) return;
    _activeSignInId = signInId;
    _fetchedAt = null;
    _isExpired = false;
    _refreshVersion++;

    if (signInId == null) {
      _value = initialValue;
      notifyListeners();
      return;
    }

    final raw = _storage.readRaw(fileName);
    if (raw != null && raw.isNotEmpty) {
      try {
        final decoded = jsonDecode(raw);
        if (decoded is Map<String, dynamic> &&
            decoded['signInId'] == signInId) {
          final fetchedRaw = decoded['fetchedAt'] as String?;
          _fetchedAt = fetchedRaw == null ? null : DateTime.parse(fetchedRaw);
          _value = _fromJson(decoded['data']);
          notifyListeners();
          return;
        }
      } catch (_) {
        // Ignore corrupted snapshot and reset cleanly.
      }
    }
    _value = initialValue;
    notifyListeners();
  }

  void expire() {
    _isExpired = true;
  }

  bool isStaleAt(
    DateTime now,
    Duration maxAge, {
    bool Function(T value)? isComplete,
  }) {
    if (_activeSignInId == null) return false;
    if (_isExpired || _fetchedAt == null) return true;
    if (isComplete != null && !isComplete(_value)) return true;
    return now.difference(_fetchedAt!) >= maxAge;
  }

  Future<void> update(
    String signInId,
    int refreshVersion,
    T Function(T current) transform,
  ) async {
    if (_activeSignInId != signInId || refreshVersion < _refreshVersion) {
      return;
    }
    _value = transform(_value);
    _fetchedAt = DateTime.now().toUtc();
    _isExpired = false;
    notifyListeners();
    await _persist(signInId);
  }

  Future<void> amend(String signInId, T Function(T current) transform) async {
    if (_activeSignInId != signInId) return;
    _value = transform(_value);
    notifyListeners();
    await _persist(signInId);
  }

  Future<void> _persist(String signInId) async {
    if (_activeSignInId != signInId) return;
    final payload = jsonEncode(<String, dynamic>{
      'signInId': signInId,
      if (_fetchedAt != null) 'fetchedAt': _fetchedAt!.toIso8601String(),
      'data': _toJson(_value),
    });
    await _storage.writeRaw(fileName, payload);
  }
}
