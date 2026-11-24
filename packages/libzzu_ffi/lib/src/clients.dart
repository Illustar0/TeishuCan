import 'dart:convert';
import 'dart:ffi';
import 'dart:typed_data';

import 'package:ffi/ffi.dart';

import 'async_bridge.dart';
import 'bindings.dart';
import 'error.dart';
import 'library_loader.dart';
import 'models.dart';
import 'runtime.dart';

Pointer<T> _constructFromRuntime<T extends NativeType>(
  LibzzuBindings bindings,
  Pointer<ZzuRuntimeOpaque> runtimeHandle,
  Map<String, dynamic> config,
  int Function(
    Pointer<ZzuRuntimeOpaque> runtime,
    Pointer<Uint8> config,
    int configLen,
    Pointer<Pointer<T>> outClient,
    Pointer<Pointer<ZzuBuffer>> outError,
  ) fn,
) {
  final bytes = utf8.encode(jsonEncode(config));
  final configPtr = calloc<Uint8>(bytes.length)
    ..asTypedList(bytes.length).setAll(0, bytes);
  final outPtr = calloc<Pointer<Void>>();
  outPtr.value = nullptr;
  final outErr = calloc<Pointer<ZzuBuffer>>();
  outErr.value = nullptr;

  try {
    final status = fn(
      runtimeHandle,
      configPtr,
      bytes.length,
      outPtr.cast<Pointer<T>>(),
      outErr,
    );
    final errText = readAndFreeBuffer(bindings, outErr.value);
    if (status != ZzuStatus.ok || outPtr.value == nullptr) {
      throw ZzuException.fromPayload(status, errText);
    }
    return outPtr.value.cast<T>();
  } finally {
    calloc.free(configPtr);
    calloc.free(outPtr);
    calloc.free(outErr);
  }
}

Pointer<T> _constructFromCas<T extends NativeType>(
  LibzzuBindings bindings,
  Pointer<ZzuCasClient> casHandle,
  int Function(
    Pointer<ZzuCasClient> cas,
    Pointer<Pointer<T>> outClient,
    Pointer<Pointer<ZzuBuffer>> outError,
  ) fn,
) {
  final outPtr = calloc<Pointer<Void>>();
  outPtr.value = nullptr;
  final outErr = calloc<Pointer<ZzuBuffer>>();
  outErr.value = nullptr;

  try {
    final status = fn(casHandle, outPtr.cast<Pointer<T>>(), outErr);
    final errText = readAndFreeBuffer(bindings, outErr.value);
    if (status != ZzuStatus.ok || outPtr.value == nullptr) {
      throw ZzuException.fromPayload(status, errText);
    }
    return outPtr.value.cast<T>();
  } finally {
    calloc.free(outPtr);
    calloc.free(outErr);
  }
}

class CasClient implements Finalizable {
  CasClient(
    ZzuRuntime runtime, {
    required String account,
    required String password,
    required String deviceId,
    int? timeoutMs,
  }) : _bindings = runtime.bindings {
    runtime.ensureNotDisposed();
    handle = _constructFromRuntime(
      _bindings,
      runtime.handle,
      <String, dynamic>{
        'account': account,
        'password': password,
        'deviceId': deviceId,
        'timeoutMs': ?timeoutMs,
      },
      _bindings.zzuCasNew,
    );
    _finalizerFor(_bindings).attach(this, handle.cast(), detach: this);
  }

  static NativeFinalizer? _cachedFinalizer;
  static LibzzuBindings? _cachedBindings;

  static NativeFinalizer _finalizerFor(LibzzuBindings bindings) {
    if (_cachedFinalizer != null && identical(_cachedBindings, bindings)) {
      return _cachedFinalizer!;
    }
    _cachedBindings = bindings;
    return _cachedFinalizer = NativeFinalizer(bindings.zzuCasFreePtr);
  }

  final LibzzuBindings _bindings;
  late final Pointer<ZzuCasClient> handle;
  bool _disposed = false;

  Future<dynamic> _call(String method, {Object? params}) {
    if (_disposed) throw StateError('CasClient has been disposed');
    return NativeCallBridge.forBindings(_bindings).invokeJson(
      method,
      (m, p, len, cb, data, out) =>
          _bindings.zzuCasCall(handle, m, p, len, cb, data, out),
      params: params,
    );
  }

  Future<void> login() async => _call('login');

  Future<void> reauthenticate() async => _call('reauthenticate');

  Future<void> logout() async => _call('logout');

  Future<bool> isLoggedIn() async => (await _call('is_logged_in')) as bool;

  Future<String> deviceId() async => (await _call('device_id')) as String;

  Future<void> setTokens(Tokens tokens) async =>
      _call('set_tokens', params: tokens.toJson());

  Future<Tokens?> tokens() async {
    final res = await _call('tokens');
    if (res == null) return null;
    return Tokens.fromJson(res as Map<String, dynamic>);
  }

  Future<String> userToken() async => (await _call('user_token')) as String;

  Future<MfaStatus> detectMfa() async =>
      MfaStatus.fromJson((await _call('detect_mfa')) as Map<String, dynamic>);

  Future<SmsPrepared> prepareSms() async =>
      SmsPrepared.fromJson((await _call('prepare_sms')) as Map<String, dynamic>);

  Future<SmsPrepared> sendSms() async =>
      SmsPrepared.fromJson((await _call('send_sms')) as Map<String, dynamic>);

  Future<void> verifySms(String code) async =>
      _call('verify_sms', params: <String, dynamic>{'code': code});

  Future<void> setDeviceTrust(String deviceId, {required bool isTrusted}) async =>
      _call(
        'set_device_trust',
        params: <String, dynamic>{
          'deviceId': deviceId,
          'isTrusted': isTrusted,
        },
      );

  Future<PersonalInfo> userInfo() async =>
      PersonalInfo.fromJson((await _call('user_info')) as Map<String, dynamic>);

  Future<String> serviceSsoUrl(SsoService service) async =>
      (await _call(
        'service_sso_url',
        params: <String, dynamic>{'service': service.jsonValue},
      )) as String;

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _finalizerFor(_bindings).detach(this);
    _bindings.zzuCasFree(handle.cast());
  }
}

class EasClient implements Finalizable {
  EasClient(CasClient cas) : _bindings = cas._bindings {
    if (cas._disposed) throw StateError('CasClient has been disposed');
    handle = _constructFromCas(_bindings, cas.handle, _bindings.zzuEasNew);
    _finalizerFor(_bindings).attach(this, handle.cast(), detach: this);
  }

  static NativeFinalizer? _cachedFinalizer;
  static LibzzuBindings? _cachedBindings;

  static NativeFinalizer _finalizerFor(LibzzuBindings bindings) {
    if (_cachedFinalizer != null && identical(_cachedBindings, bindings)) {
      return _cachedFinalizer!;
    }
    _cachedBindings = bindings;
    return _cachedFinalizer = NativeFinalizer(bindings.zzuEasFreePtr);
  }

  final LibzzuBindings _bindings;
  late final Pointer<ZzuEasClient> handle;
  bool _disposed = false;

  Future<dynamic> _call(String method, {Object? params}) {
    if (_disposed) throw StateError('EasClient has been disposed');
    return NativeCallBridge.forBindings(_bindings).invokeJson(
      method,
      (m, p, len, cb, data, out) =>
          _bindings.zzuEasCall(handle, m, p, len, cb, data, out),
      params: params,
    );
  }

  Future<void> login() async => _call('login');

  Future<void> logout() async => _call('logout');

  Future<bool> isLoggedIn() async => (await _call('is_logged_in')) as bool;

  Future<Semester> currentSemester() async =>
      Semester.fromJson((await _call('current_semester')) as Map<String, dynamic>);

  Future<List<Semester>> semesters() async {
    final list = (await _call('semesters')) as List<dynamic>;
    return list
        .map((e) => Semester.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<Timetable> timetable({
    int? semesterId,
    bool includeExperiments = true,
  }) async {
    final json = (await _call(
      'timetable',
      params: <String, dynamic>{
        'semesterId': ?semesterId,
        'includeExperiments': includeExperiments,
      },
    )) as Map<String, dynamic>;
    return Timetable.fromJson(json);
  }

  Future<ClassroomCatalog> classroomCatalog() async => ClassroomCatalog.fromJson(
        (await _call('classroom_catalog')) as Map<String, dynamic>,
      );

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _finalizerFor(_bindings).detach(this);
    _bindings.zzuEasFree(handle.cast());
  }
}

class EcardClient implements Finalizable {
  EcardClient(CasClient cas) : _bindings = cas._bindings {
    if (cas._disposed) throw StateError('CasClient has been disposed');
    handle = _constructFromCas(_bindings, cas.handle, _bindings.zzuEcardNew);
    _finalizerFor(_bindings).attach(this, handle.cast(), detach: this);
  }

  static NativeFinalizer? _cachedFinalizer;
  static LibzzuBindings? _cachedBindings;

  static NativeFinalizer _finalizerFor(LibzzuBindings bindings) {
    if (_cachedFinalizer != null && identical(_cachedBindings, bindings)) {
      return _cachedFinalizer!;
    }
    _cachedBindings = bindings;
    return _cachedFinalizer = NativeFinalizer(bindings.zzuEcardFreePtr);
  }

  final LibzzuBindings _bindings;
  late final Pointer<ZzuEcardClient> handle;
  bool _disposed = false;

  Future<dynamic> _call(String method, {Object? params}) {
    if (_disposed) throw StateError('EcardClient has been disposed');
    return NativeCallBridge.forBindings(_bindings).invokeJson(
      method,
      (m, p, len, cb, data, out) =>
          _bindings.zzuEcardCall(handle, m, p, len, cb, data, out),
      params: params,
    );
  }

  Future<void> login() async => _call('login');

  Future<void> logout() async => _call('logout');

  Future<bool> isLoggedIn() async => (await _call('is_logged_in')) as bool;

  Future<String> defaultRoom() async => (await _call('default_room')) as String;

  Future<String> balance() async => (await _call('balance')) as String;

  Future<List<LocationOption>> locations(String parent) async {
    final list = (await _call(
      'locations',
      params: <String, dynamic>{'parent': parent},
    )) as List<dynamic>;
    return list
        .map((e) => LocationOption.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<double> remainingEnergy({String? room}) async {
    final result = await _call(
      'remaining_energy',
      params: room == null ? null : <String, dynamic>{'room': room},
    );
    return (result as num).toDouble();
  }

  Future<void> rechargeEnergy({
    required String paymentPassword,
    required int amount,
    required String room,
  }) async {
    await _call(
      'recharge_energy',
      params: <String, dynamic>{
        'paymentPassword': paymentPassword,
        'amount': amount,
        'room': room,
      },
    );
  }

  Future<RechargeConfig> rechargeConfig() async => RechargeConfig.fromJson(
        (await _call('recharge_config')) as Map<String, dynamic>,
      );

  Future<RechargeOrder> createRechargeOrder(int amount) async =>
      RechargeOrder.fromJson(
        (await _call(
          'create_recharge_order',
          params: <String, dynamic>{'amount': amount},
        )) as Map<String, dynamic>,
      );

  Future<List<PaymentChannel>> paymentChannels(RechargeOrder order) async {
    final list = (await _call(
      'payment_channels',
      params: <String, dynamic>{'order': order.rawJson},
    )) as List<dynamic>;
    return list
        .map((e) => PaymentChannel.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<PaymentAction> preparePayment(
    RechargeOrder order,
    PaymentChannel channel, {
    String? device,
    String? payerIp,
  }) async {
    final context = <String, dynamic>{
      'device': ?device,
      'payerIp': ?payerIp,
    };
    return PaymentAction.fromJson(
      (await _call(
        'prepare_payment',
        params: <String, dynamic>{
          'order': order.rawJson,
          'channel': channel.toJson(),
          if (context.isNotEmpty) 'context': context,
        },
      )) as Map<String, dynamic>,
    );
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _finalizerFor(_bindings).detach(this);
    _bindings.zzuEcardFree(handle.cast());
  }
}

class MailClient implements Finalizable {
  MailClient(CasClient cas) : _bindings = cas._bindings {
    if (cas._disposed) throw StateError('CasClient has been disposed');
    handle = _constructFromCas(_bindings, cas.handle, _bindings.zzuMailNew);
    _finalizerFor(_bindings).attach(this, handle.cast(), detach: this);
  }

  static NativeFinalizer? _cachedFinalizer;
  static LibzzuBindings? _cachedBindings;

  static NativeFinalizer _finalizerFor(LibzzuBindings bindings) {
    if (_cachedFinalizer != null && identical(_cachedBindings, bindings)) {
      return _cachedFinalizer!;
    }
    _cachedBindings = bindings;
    return _cachedFinalizer = NativeFinalizer(bindings.zzuMailFreePtr);
  }

  final LibzzuBindings _bindings;
  late final Pointer<ZzuMailClient> handle;
  bool _disposed = false;

  Future<dynamic> _call(String method, {Object? params}) {
    if (_disposed) throw StateError('MailClient has been disposed');
    return NativeCallBridge.forBindings(_bindings).invokeJson(
      method,
      (m, p, len, cb, data, out) =>
          _bindings.zzuMailCall(handle, m, p, len, cb, data, out),
      params: params,
    );
  }

  Future<void> login() async => _call('login');

  Future<void> logout() async => _call('logout');

  Future<bool> isLoggedIn() async => (await _call('is_logged_in')) as bool;

  Future<String> webmailUrl() async => (await _call('webmail_url')) as String;

  Future<int> unreadCount() async => ((await _call('unread_count')) as num).toInt();

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _finalizerFor(_bindings).detach(this);
    _bindings.zzuMailFree(handle.cast());
  }
}

Future<PortalDiscovery> portalDiscover(
  ZzuRuntime runtime, {
  int? timeoutMs,
}) async {
  runtime.ensureNotDisposed();
  final json = await NativeCallBridge.forBindings(runtime.bindings)
      .invokeOptionsJson(
    (opts, len, cb, data, out) => runtime.bindings.zzuPortalDiscover(
      runtime.handle,
      opts,
      len,
      cb,
      data,
      out,
    ),
    options: timeoutMs == null ? null : <String, dynamic>{'timeoutMs': timeoutMs},
  );
  return PortalDiscovery.fromJson(json as Map<String, dynamic>);
}

class PortalClient implements Finalizable {
  PortalClient(
    ZzuRuntime runtime, {
    required String baseUrl,
    required String userIp,
    String? sourceAddress,
    int? timeoutMs,
  }) : _bindings = runtime.bindings {
    runtime.ensureNotDisposed();
    handle = _constructFromRuntime(
      _bindings,
      runtime.handle,
      <String, dynamic>{
        'baseUrl': baseUrl,
        'userIp': userIp,
        'sourceAddress': ?sourceAddress,
        'timeoutMs': ?timeoutMs,
      },
      _bindings.zzuPortalNew,
    );
    _finalizerFor(_bindings).attach(this, handle.cast(), detach: this);
  }

  static NativeFinalizer? _cachedFinalizer;
  static LibzzuBindings? _cachedBindings;

  static NativeFinalizer _finalizerFor(LibzzuBindings bindings) {
    if (_cachedFinalizer != null && identical(_cachedBindings, bindings)) {
      return _cachedFinalizer!;
    }
    _cachedBindings = bindings;
    return _cachedFinalizer = NativeFinalizer(bindings.zzuPortalFreePtr);
  }

  final LibzzuBindings _bindings;
  late final Pointer<ZzuPortalClient> handle;
  bool _disposed = false;

  Future<dynamic> _call(String method, {Object? params}) {
    if (_disposed) throw StateError('PortalClient has been disposed');
    return NativeCallBridge.forBindings(_bindings).invokeJson(
      method,
      (m, p, len, cb, data, out) =>
          _bindings.zzuPortalCall(handle, m, p, len, cb, data, out),
      params: params,
    );
  }

  Future<PortalAuth> login({
    required String account,
    required String password,
    String? encryption,
  }) async {
    final json = (await _call(
      'login',
      params: <String, dynamic>{
        'account': account,
        'password': password,
        'encryption': ?encryption,
      },
    )) as Map<String, dynamic>;
    return PortalAuth.fromJson(json);
  }

  Future<List<IspInfo>> isps() async {
    final list = (await _call('isps')) as List<dynamic>;
    return list
        .map((e) => IspInfo.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _finalizerFor(_bindings).detach(this);
    _bindings.zzuPortalFree(handle.cast());
  }
}

class SelfServiceClient implements Finalizable {
  SelfServiceClient(
    ZzuRuntime runtime, {
    required String baseUrl,
    int? timeoutMs,
  }) : _bindings = runtime.bindings {
    runtime.ensureNotDisposed();
    handle = _constructFromRuntime(
      _bindings,
      runtime.handle,
      <String, dynamic>{
        'baseUrl': baseUrl,
        'timeoutMs': ?timeoutMs,
      },
      _bindings.zzuSelfServiceNew,
    );
    _finalizerFor(_bindings).attach(this, handle.cast(), detach: this);
  }

  static NativeFinalizer? _cachedFinalizer;
  static LibzzuBindings? _cachedBindings;

  static NativeFinalizer _finalizerFor(LibzzuBindings bindings) {
    if (_cachedFinalizer != null && identical(_cachedBindings, bindings)) {
      return _cachedFinalizer!;
    }
    _cachedBindings = bindings;
    return _cachedFinalizer = NativeFinalizer(bindings.zzuSelfServiceFreePtr);
  }

  final LibzzuBindings _bindings;
  late final Pointer<ZzuSelfServiceClient> handle;
  bool _disposed = false;

  Future<dynamic> _call(String method, {Object? params}) {
    if (_disposed) throw StateError('SelfServiceClient has been disposed');
    return NativeCallBridge.forBindings(_bindings).invokeJson(
      method,
      (m, p, len, cb, data, out) =>
          _bindings.zzuSelfServiceCall(handle, m, p, len, cb, data, out),
      params: params,
    );
  }

  Future<void> login({
    required String account,
    required String password,
  }) async {
    await _call(
      'login',
      params: <String, dynamic>{
        'account': account,
        'password': password,
      },
    );
  }

  Future<bool> isLoggedIn() async => (await _call('is_logged_in')) as bool;

  Future<List<OnlineDevice>> onlineDevices() async {
    final list = (await _call('online_devices')) as List<dynamic>;
    return list
        .map((e) => OnlineDevice.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<void> kickDevice(String sessionId) async =>
      _call('kick_device', params: <String, dynamic>{'sessionId': sessionId});

  Future<void> logout() async => _call('logout');

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _finalizerFor(_bindings).detach(this);
    _bindings.zzuSelfServiceFree(handle.cast());
  }
}

String parseRoomId(String room, {String? libraryPath}) {
  final bindings = loadLibzzuBindings(libraryPath: libraryPath);
  final bytes = Uint8List.fromList(utf8.encode(room));
  return invokeLocalJson(bindings, bindings.zzuRoomIdParse, bytes) as String;
}

String timetableToCalendar(
  Timetable timetable, {
  CalendarExportOptions? options,
  String? libraryPath,
}) {
  final bindings = loadLibzzuBindings(libraryPath: libraryPath);
  final payload = <String, dynamic>{
    'timetable': timetable.rawJson,
    if (options != null) 'options': options.toJson(),
  };
  final bytes = Uint8List.fromList(utf8.encode(jsonEncode(payload)));
  return invokeLocalJson(bindings, bindings.zzuTimetableToCalendar, bytes)
      as String;
}

String rechargeOrderCheckoutUrlFor(
  RechargeOrder order,
  PaymentChannel channel, {
  String? libraryPath,
}) {
  final bindings = loadLibzzuBindings(libraryPath: libraryPath);
  final payload = <String, dynamic>{
    'order': order.rawJson,
    'channel': channel.toJson(),
  };
  final bytes = Uint8List.fromList(utf8.encode(jsonEncode(payload)));
  return invokeLocalJson(
    bindings,
    bindings.zzuRechargeOrderCheckoutUrlFor,
    bytes,
  ) as String;
}
