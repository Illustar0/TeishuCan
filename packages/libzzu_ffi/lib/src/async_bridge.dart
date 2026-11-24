import 'dart:async';
import 'dart:convert';
import 'dart:ffi';
import 'dart:typed_data';

import 'package:ffi/ffi.dart';

import 'bindings.dart';
import 'error.dart';

typedef ZzuCallSubmitFn = int Function(
  Pointer<Utf8> method,
  Pointer<Uint8> params,
  int paramsLen,
  Pointer<NativeFunction<ZzuCallbackNative>> callback,
  Pointer<Void> userData,
  Pointer<Pointer<ZzuCall>> outCall,
);

typedef ZzuNoMethodCallSubmitFn = int Function(
  Pointer<Uint8> options,
  int optionsLen,
  Pointer<NativeFunction<ZzuCallbackNative>> callback,
  Pointer<Void> userData,
  Pointer<Pointer<ZzuCall>> outCall,
);

typedef ZzuLocalBytesCallFn = int Function(
  Pointer<Uint8> input,
  int inputLen,
  Pointer<Pointer<ZzuBuffer>> outPayload,
);

class NativeCallBridge {
  NativeCallBridge._(this.bindings) {
    _callable = NativeCallable<ZzuCallbackNative>.listener(_onNativeCallback)
      ..keepIsolateAlive = false;
  }

  static NativeCallBridge? _instance;

  static NativeCallBridge forBindings(LibzzuBindings bindings) {
    final existing = _instance;
    if (existing != null && identical(existing.bindings, bindings)) {
      return existing;
    }
    final created = NativeCallBridge._(bindings);
    _instance = created;
    return created;
  }

  final LibzzuBindings bindings;
  late final NativeCallable<ZzuCallbackNative> _callable;
  final Map<int, Completer<String>> _pending = <int, Completer<String>>{};
  int _nextToken = 1;

  void _onNativeCallback(
    Pointer<Void> userData,
    int status,
    Pointer<ZzuBuffer> payload,
  ) {
    String text;
    try {
      text = readAndFreeBuffer(bindings, payload);
    } catch (error, stackTrace) {
      final token = userData.address;
      final completer = _pending.remove(token);
      completer?.completeError(error, stackTrace);
      return;
    }

    final token = userData.address;
    final completer = _pending.remove(token);
    if (completer == null || completer.isCompleted) {
      return;
    }
    if (status == ZzuStatus.ok) {
      completer.complete(text);
    } else {
      completer.completeError(ZzuException.fromPayload(status, text));
    }
  }

  int _allocateToken(Completer<String> completer) {
    for (var attempts = 0; attempts < 0x3FFFFFFF; attempts++) {
      final candidate = _nextToken;
      _nextToken = _nextToken >= 0x3FFFFFFF ? 1 : _nextToken + 1;
      if (!_pending.containsKey(candidate)) {
        _pending[candidate] = completer;
        return candidate;
      }
    }
    throw StateError('Exhausted pending async call token space');
  }

  Future<dynamic> invokeJson(
    String method,
    ZzuCallSubmitFn submit, {
    Object? params,
  }) async {
    final raw = await invokeRaw(
      method,
      submit,
      paramsBytes: encodeJsonParams(params),
    );
    if (raw.isEmpty) return null;
    return jsonDecode(raw);
  }

  Future<String> invokeRaw(
    String method,
    ZzuCallSubmitFn submit, {
    Uint8List? paramsBytes,
  }) {
    final completer = Completer<String>();
    final token = _allocateToken(completer);

    final methodPtr = method.toNativeUtf8();
    final paramsLen = paramsBytes?.length ?? 0;
    final Pointer<Uint8> paramsPtr = paramsLen > 0
        ? (calloc<Uint8>(paramsLen)..asTypedList(paramsLen).setAll(0, paramsBytes!))
        : nullptr;
    final outCallPtr = calloc<Pointer<ZzuCall>>();
    outCallPtr.value = nullptr;

    try {
      final submitStatus = submit(
        methodPtr,
        paramsPtr,
        paramsLen,
        _callable.nativeFunction,
        Pointer<Void>.fromAddress(token),
        outCallPtr,
      );
      if (submitStatus != ZzuStatus.ok) {
        _pending.remove(token);
        throw ZzuException.fromPayload(submitStatus, '');
      }
      final callHandle = outCallPtr.value;
      if (callHandle != nullptr) {
        bindings.zzuCallFree(callHandle);
      }
    } finally {
      calloc.free(methodPtr);
      if (paramsPtr != nullptr) {
        calloc.free(paramsPtr);
      }
      calloc.free(outCallPtr);
    }

    return completer.future;
  }

  Future<dynamic> invokeOptionsJson(
    ZzuNoMethodCallSubmitFn submit, {
    Map<String, dynamic>? options,
  }) async {
    final completer = Completer<String>();
    final token = _allocateToken(completer);

    final optionsBytes = encodeJsonParams(options);
    final optionsLen = optionsBytes?.length ?? 0;
    final Pointer<Uint8> optionsPtr = optionsLen > 0
        ? (calloc<Uint8>(optionsLen)
          ..asTypedList(optionsLen).setAll(0, optionsBytes!))
        : nullptr;
    final outCallPtr = calloc<Pointer<ZzuCall>>();
    outCallPtr.value = nullptr;

    try {
      final submitStatus = submit(
        optionsPtr,
        optionsLen,
        _callable.nativeFunction,
        Pointer<Void>.fromAddress(token),
        outCallPtr,
      );
      if (submitStatus != ZzuStatus.ok) {
        _pending.remove(token);
        throw ZzuException.fromPayload(submitStatus, '');
      }
      final callHandle = outCallPtr.value;
      if (callHandle != nullptr) {
        bindings.zzuCallFree(callHandle);
      }
    } finally {
      if (optionsPtr != nullptr) {
        calloc.free(optionsPtr);
      }
      calloc.free(outCallPtr);
    }

    final raw = await completer.future;
    if (raw.isEmpty) return null;
    return jsonDecode(raw);
  }
}

Uint8List? encodeJsonParams(Object? params) {
  if (params == null) return null;
  return Uint8List.fromList(utf8.encode(jsonEncode(params)));
}

String readAndFreeBuffer(LibzzuBindings bindings, Pointer<ZzuBuffer> buffer) {
  if (buffer == nullptr) return '';
  try {
    final len = bindings.zzuBufferLen(buffer);
    if (len == 0) return '';
    final data = bindings.zzuBufferData(buffer);
    if (data == nullptr) return '';
    return utf8.decode(data.asTypedList(len));
  } finally {
    bindings.zzuBufferFree(buffer);
  }
}

dynamic invokeLocalJson(
  LibzzuBindings bindings,
  ZzuLocalBytesCallFn fn,
  Uint8List inputBytes,
) {
  final inputLen = inputBytes.length;
  final Pointer<Uint8> inputPtr = inputLen > 0
      ? (calloc<Uint8>(inputLen)..asTypedList(inputLen).setAll(0, inputBytes))
      : nullptr;
  final outPayloadPtr = calloc<Pointer<ZzuBuffer>>();
  outPayloadPtr.value = nullptr;

  try {
    final status = fn(inputPtr, inputLen, outPayloadPtr);
    final payloadText = readAndFreeBuffer(bindings, outPayloadPtr.value);
    if (status != ZzuStatus.ok) {
      throw ZzuException.fromPayload(status, payloadText);
    }
    if (payloadText.isEmpty) return null;
    return jsonDecode(payloadText);
  } finally {
    if (inputPtr != nullptr) {
      calloc.free(inputPtr);
    }
    calloc.free(outPayloadPtr);
  }
}
