import 'package:libzzu_ffi/libzzu_ffi.dart';
import 'package:uuid/uuid.dart';

import '../domain/account.dart';
import 'async_mutex.dart';
import 'session_store.dart';

class ZzuClients {
  ZzuClients(
    ZzuRuntime runtime, {
    required this.account,
    required this.password,
    required String deviceId,
  }) : cas = CasClient(
          runtime,
          account: account,
          password: password,
          deviceId: deviceId,
        ) {
    try {
      _eas = EasClient(cas);
      _ecard = EcardClient(cas);
    } catch (_) {
      _eas?.dispose();
      cas.dispose();
      rethrow;
    }
  }

  final String account;
  final String password;
  final CasClient cas;
  EasClient? _eas;
  late final EcardClient _ecard;

  final AsyncMutex _easLogin = AsyncMutex();
  final AsyncMutex _ecardLogin = AsyncMutex();
  final AsyncMutex _casAccess = AsyncMutex();
  bool _disposed = false;

  EasClient get easClient => _eas!;
  EcardClient get ecardClient => _ecard;

  Future<EasClient> eas() => _easLogin.synchronized(() async {
        final client = easClient;
        if (!await client.isLoggedIn()) {
          await client.login();
        }
        return client;
      });

  Future<EcardClient> ecard() => _ecardLogin.synchronized(() async {
        if (!await _ecard.isLoggedIn()) {
          await _ecard.login();
        }
        return _ecard;
      });

  Future<void> ensureSignedIn(
    Future<void> Function(Tokens tokens) saveTokens,
  ) =>
      _casAccess.synchronized(() async {
        if (!await cas.isLoggedIn()) {
          await cas.login();
          await saveTokens(await _requireTokens());
        }
        try {
          await cas.userToken();
        } on ZzuAuthenticationException catch (cause) {
          if (cause.kind != AuthErrorKind.tokenExpired) rethrow;
          await cas.reauthenticate();
          await saveTokens(await _requireTokens());
        }
      });

  Future<void> presetTokens(Tokens tokens) async {
    try {
      await cas.setTokens(tokens);
    } on ZzuInvalidInputException {
      // Expired or invalid saved tokens fall back to password login on ensureSignedIn.
    }
  }

  Future<StoredSession> storedSession(
    StudentProfile profile,
    DateTime profileFetchedAt,
  ) async =>
      StoredSession(
        signInId: const Uuid().v4(),
        account: account,
        password: password,
        tokens: await _requireTokens(),
        profile: profile,
        profileFetchedAt: profileFetchedAt,
      );

  Future<Tokens> _requireTokens() async {
    final tokens = await cas.tokens();
    if (tokens == null) {
      throw StateError('Missing session tokens');
    }
    return tokens;
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _eas?.dispose();
    _ecard.dispose();
    cas.dispose();
  }
}
