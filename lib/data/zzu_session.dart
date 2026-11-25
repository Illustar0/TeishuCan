import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:libzzu_ffi/libzzu_ffi.dart';

import '../domain/account.dart';
import '../domain/campus_exception.dart';
import 'async_mutex.dart';
import 'session_store.dart';
import 'snapshot_cache.dart';
import 'zzu_clients.dart';

const Set<String> _signInEndingFailures = <String>{
  AuthErrorKind.invalidCredentials,
  AuthErrorKind.tokenExpired,
  AuthErrorKind.notAuthenticated,
  AuthErrorKind.mfaRequired,
};

typedef RuntimeFactory = Future<ZzuRuntime> Function();

class ZzuSessionController extends ChangeNotifier {
  ZzuSessionController({
    required this._store,
    required this._snapshots,
    RuntimeFactory? runtimeFactory,
  }) : _runtimeFactory = runtimeFactory ?? (() => ZzuRuntime.create());

  final SessionStore _store;
  final SnapshotStorage _snapshots;
  final RuntimeFactory _runtimeFactory;
  final AsyncMutex _transitionLock = AsyncMutex();
  final List<void Function(String? signInId)> _sessionListeners =
      <void Function(String? signInId)>[];

  ZzuRuntime? _runtime;
  ZzuClients? _clients;
  StoredSession? _savedSession;
  String? _deviceId;
  AccountState _account = const AccountRestoring();
  bool _isLoggingIn = false;
  bool _isProfileRefreshing = false;
  int _profileRefreshVersion = 0;
  ZzuClients? _lastProfileOwner;
  int _lastProfileVersion = 0;
  final Completer<void> _restoredCompleter = Completer<void>();

  AccountState get account => _account;
  bool get isProfileRefreshing => _isProfileRefreshing;
  String? get currentSignInId => _savedSession?.signInId;
  String? get savedAccount => _savedSession?.account;
  String? get savedPassword => _savedSession?.password;

  Future<ZzuRuntime> ensureRuntime() => _ensureRuntime();

  void addSessionBinding(void Function(String? signInId) onSessionChange) {
    _sessionListeners.add(onSessionChange);
    onSessionChange(_savedSession?.signInId);
  }

  Future<void> init() async {
    try {
      final saved = await _store.readSession();
      if (saved == null) {
        await _snapshots.clearAll();
        _account = const AccountSignedOut();
      } else {
        final runtime = await _ensureRuntime();
        final deviceId = await _ensureDeviceId();
        final clients = ZzuClients(
          runtime,
          account: saved.account,
          password: saved.password,
          deviceId: deviceId,
        );
        try {
          await clients.presetTokens(saved.tokens);
        } catch (_) {
          clients.dispose();
          rethrow;
        }
        _clients = clients;
        _savedSession = saved;
        _account = AccountSignedIn(saved.profile);
        _notifySessionBindings(saved.signInId);
      }
    } catch (cause) {
      debugPrint('Could not restore the saved session: $cause');
      await _snapshots.clearAll();
      _account = const AccountSignedOut();
    } finally {
      if (!_restoredCompleter.isCompleted) {
        _restoredCompleter.complete();
      }
      notifyListeners();
    }
  }

  Future<ZzuRuntime> _ensureRuntime() async {
    return _runtime ??= await _runtimeFactory();
  }

  Future<String> _ensureDeviceId() async {
    return _deviceId ??= await _store.getOrCreateDeviceId();
  }

  Future<LoginResult> login(String account, String password) async {
    assert(account.trim().isNotEmpty && password.isNotEmpty);
    await _restoredCompleter.future;
    return _accountStep(() async {
      final runtime = await _ensureRuntime();
      final deviceId = await _ensureDeviceId();
      final clients = ZzuClients(
        runtime,
        account: account,
        password: password,
        deviceId: deviceId,
      );
      await _replaceState(newClients: clients, loggingIn: true);
      try {
        try {
          await clients.cas.login();
          await _finishLogin(clients);
          return const LoginSignedIn();
        } on ZzuAuthenticationException catch (cause) {
          if (cause.kind != AuthErrorKind.mfaRequired) rethrow;
          final challenge = await clients.cas.prepareSms();
          return LoginMfaRequired(
            securePhone: _normalizedPhone(challenge.securePhone),
          );
        }
      } catch (_) {
        await _end(clients);
        rethrow;
      }
    });
  }

  Future<String?> sendMfaCode() async {
    final clients = _awaitingMfa();
    return _accountStep(() async {
      final prepared = await clients.cas.sendSms();
      return _normalizedPhone(prepared.securePhone);
    });
  }

  Future<void> verifyMfa(String code, {bool shouldTrustDevice = false}) async {
    final clients = _awaitingMfa();
    await _accountStep(() async {
      await clients.cas.verifySms(code);
      await clients.cas.login();
      if (shouldTrustDevice) {
        await _trustCurrentDevice(clients);
      }
      await _finishLogin(clients);
    });
  }

  Future<void> _trustCurrentDevice(ZzuClients clients) async {
    try {
      final deviceId = await _ensureDeviceId();
      await clients.cas.setDeviceTrust(deviceId, isTrusted: true);
    } on ZzuException catch (cause) {
      debugPrint('Could not trust this device: $cause');
    }
  }

  void abandonLogin() {
    final pending = _isLoggingIn ? _clients : null;
    if (pending == null) return;
    unawaited(_end(pending));
  }

  Future<void> logout() async {
    await _restoredCompleter.future;
    final current = _savedSession == null ? null : _clients;
    if (current == null) return;
    await _end(current);
  }

  Future<T> request<T>(
    CampusFeature feature,
    Future<T> Function(ZzuClients clients, String signInId) action,
  ) async {
    await _restoredCompleter.future;
    final clients = _clients;
    final session = _savedSession;
    if (clients == null || session == null) {
      throw CampusRequestException(
        feature,
        cause: StateError('Sign in first'),
      );
    }

    try {
      await clients.ensureSignedIn((tokens) async {
        await _updateSession(clients, (s) => s.copyWith(tokens: tokens));
      });
      return await action(clients, session.signInId);
    } catch (cause) {
      await _endOnAuthenticationFailure(clients, cause);
      debugPrint('$feature request failed: $cause');
      throw CampusRequestException(
        feature,
        reason: cause is ZzuException ? cause.message : null,
        cause: cause,
      );
    }
  }

  bool isProfileStaleAt(DateTime now, Duration maxAge) {
    final session = _savedSession;
    if (session == null) return false;
    final fetchedAt = session.profileFetchedAt;
    if (fetchedAt == null) return true;
    return now.difference(fetchedAt) >= maxAge;
  }

  Future<void> refreshProfile() async {
    if (_isProfileRefreshing) return;
    _isProfileRefreshing = true;
    notifyListeners();
    final version = ++_profileRefreshVersion;
    try {
      await request(CampusFeature.profile, (clients, _) async {
        final info = await clients.cas.userInfo();
        final profile = _toStudentProfile(info);
        await _saveProfile(clients, version, profile, DateTime.now().toUtc());
      });
    } finally {
      _isProfileRefreshing = false;
      notifyListeners();
    }
  }

  Future<void> _saveProfile(
    ZzuClients owner,
    int refreshVersion,
    StudentProfile profile,
    DateTime fetchedAt,
  ) =>
      _updateSession(owner, (session) {
        if (identical(_lastProfileOwner, owner) &&
            _lastProfileVersion > refreshVersion) {
          return session;
        }
        _lastProfileOwner = owner;
        _lastProfileVersion = refreshVersion;
        return session.copyWith(profile: profile, profileFetchedAt: fetchedAt);
      });

  Future<T> _accountStep<T>(Future<T> Function() step) async {
    try {
      return await step();
    } on ZzuException catch (cause) {
      throw CampusRequestException(
        CampusFeature.account,
        reason: cause.message,
        cause: cause,
      );
    }
  }

  Future<void> _finishLogin(ZzuClients clients) async {
    final version = ++_profileRefreshVersion;
    final info = await clients.cas.userInfo();
    final profile = _toStudentProfile(info);
    final saved = await clients.storedSession(profile, DateTime.now().toUtc());
    await _transitionLock.synchronized(() async {
      if (!identical(_clients, clients)) {
        throw StateError('The login was abandoned');
      }
      await _store.writeSession(saved);
      _lastProfileOwner = clients;
      _lastProfileVersion = version;
      _isLoggingIn = false;
      _savedSession = saved;
      _account = AccountSignedIn(saved.profile);
      _notifySessionBindings(saved.signInId);
      notifyListeners();
    });
  }

  ZzuClients _awaitingMfa() {
    final pending = _isLoggingIn ? _clients : null;
    if (pending == null) {
      throw StateError('Verification has expired');
    }
    return pending;
  }

  Future<void> _updateSession(
    ZzuClients owner,
    StoredSession Function(StoredSession current) transform,
  ) =>
      _transitionLock.synchronized(() async {
        final current = _savedSession;
        if (current == null || !identical(_clients, owner)) return;
        final updated = transform(current);
        await _store.writeSession(updated);
        _savedSession = updated;
        _account = AccountSignedIn(updated.profile);
        notifyListeners();
      });

  Future<void> _end(ZzuClients owner) => _replaceState(
        condition: () => identical(_clients, owner),
      );

  Future<void> _endOnAuthenticationFailure(
    ZzuClients owner,
    Object cause,
  ) async {
    if (cause is ZzuAuthenticationException &&
        _signInEndingFailures.contains(cause.kind)) {
      try {
        await _end(owner);
      } catch (_) {
        // Preserve original request failure.
      }
    }
  }

  Future<void> _replaceState({
    ZzuClients? newClients,
    bool loggingIn = false,
    bool Function()? condition,
  }) async {
    ZzuClients? replaced;
    await _transitionLock.synchronized(() async {
      if (condition != null && !condition()) return;
      final hadSession = _savedSession != null;
      replaced = _clients;
      if (hadSession) {
        await _store.clearSession();
      }
      _clients = newClients;
      _isLoggingIn = loggingIn;
      _savedSession = null;
      _account = const AccountSignedOut();
      _notifySessionBindings(null);
      if (hadSession) {
        await _snapshots.clearAll();
      }
      notifyListeners();
    });
    replaced?.dispose();
  }

  void _notifySessionBindings(String? signInId) {
    for (final listener in _sessionListeners) {
      listener(signInId);
    }
  }

  @override
  void dispose() {
    _clients?.dispose();
    _runtime?.dispose();
    super.dispose();
  }
}

StudentProfile _toStudentProfile(PersonalInfo info) => StudentProfile(
      name: info.name,
      faculty: info.organizationName,
      identityType: info.identityTypeName,
    );

String? _normalizedPhone(String? phone) {
  final trimmed = phone?.trim();
  return (trimmed == null || trimmed.isEmpty) ? null : trimmed;
}
