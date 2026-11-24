import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:libzzu_ffi/libzzu_ffi.dart';
import 'package:uuid/uuid.dart';

import '../domain/account.dart';

abstract interface class KeyValueSecureStore {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
}

class FlutterSecureStorageAdapter implements KeyValueSecureStore {
  FlutterSecureStorageAdapter([FlutterSecureStorage? storage])
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
              iOptions: IOSOptions(
                accessibility: KeychainAccessibility.first_unlock_this_device,
              ),
            );

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) =>
      _storage.write(key: key, value: value);

  @override
  Future<void> delete(String key) => _storage.delete(key: key);
}

class StoredSession {
  const StoredSession({
    required this.signInId,
    required this.account,
    required this.password,
    required this.tokens,
    required this.profile,
    this.profileFetchedAt,
  });

  factory StoredSession.fromJson(Map<String, dynamic> json) {
    final fetchedRaw = json['profileFetchedAt'] as String?;
    return StoredSession(
      signInId: json['signInId'] as String,
      account: json['account'] as String,
      password: json['password'] as String,
      tokens: Tokens.fromJson(json['tokens'] as Map<String, dynamic>),
      profile: StudentProfile.fromJson(json['profile'] as Map<String, dynamic>),
      profileFetchedAt: fetchedRaw == null ? null : DateTime.parse(fetchedRaw),
    );
  }

  final String signInId;
  final String account;
  final String password;
  final Tokens tokens;
  final StudentProfile profile;
  final DateTime? profileFetchedAt;

  StoredSession copyWith({
    Tokens? tokens,
    StudentProfile? profile,
    DateTime? profileFetchedAt,
  }) =>
      StoredSession(
        signInId: signInId,
        account: account,
        password: password,
        tokens: tokens ?? this.tokens,
        profile: profile ?? this.profile,
        profileFetchedAt: profileFetchedAt ?? this.profileFetchedAt,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'signInId': signInId,
        'account': account,
        'password': password,
        'tokens': tokens.toJson(),
        'profile': profile.toJson(),
        if (profileFetchedAt != null)
          'profileFetchedAt': profileFetchedAt!.toUtc().toIso8601String(),
      };

  @override
  String toString() => 'StoredSession(<redacted>)';
}

class SessionStore {
  SessionStore(this._secure);

  static const String _sessionKey = 'teishucan.zzu.session.v1';
  static const String _deviceIdKey = 'teishucan.zzu.device_id.v1';

  final KeyValueSecureStore _secure;

  Future<String> getOrCreateDeviceId() async {
    final existing = await _secure.read(_deviceIdKey);
    if (existing != null && existing.trim().isNotEmpty) {
      return existing;
    }
    final created = 'TeishuCan(${const Uuid().v4()})';
    await _secure.write(_deviceIdKey, created);
    return created;
  }

  Future<StoredSession?> readSession() async {
    final raw = await _secure.read(_sessionKey);
    if (raw == null || raw.isEmpty) return null;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic>) {
        await clearSession();
        return null;
      }
      return StoredSession.fromJson(decoded);
    } catch (_) {
      await clearSession();
      return null;
    }
  }

  Future<void> writeSession(StoredSession session) =>
      _secure.write(_sessionKey, jsonEncode(session.toJson()));

  Future<void> clearSession() => _secure.delete(_sessionKey);
}
