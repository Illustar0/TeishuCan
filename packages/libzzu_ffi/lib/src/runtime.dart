import 'dart:convert';
import 'dart:ffi';
import 'dart:io';

import 'package:ffi/ffi.dart';
import 'package:flutter/services.dart';

import 'async_bridge.dart';
import 'bindings.dart';
import 'error.dart';
import 'library_loader.dart';

class ZzuRuntime implements Finalizable {
  ZzuRuntime._(this.bindings, this.handle) {
    _finalizerFor(bindings).attach(this, handle.cast(), detach: this);
  }

  static const MethodChannel _channel = MethodChannel('com.illustar0.libzzu_ffi');

  static NativeFinalizer? _cachedFinalizer;
  static LibzzuBindings? _cachedFinalizerBindings;

  static NativeFinalizer _finalizerFor(LibzzuBindings bindings) {
    if (_cachedFinalizer != null &&
        identical(_cachedFinalizerBindings, bindings)) {
      return _cachedFinalizer!;
    }
    _cachedFinalizerBindings = bindings;
    return _cachedFinalizer = NativeFinalizer(bindings.zzuRuntimeFreePtr);
  }

  final LibzzuBindings bindings;
  final Pointer<ZzuRuntimeOpaque> handle;
  bool _disposed = false;

  bool get isDisposed => _disposed;

  static Future<ZzuRuntime> create({
    int? workerThreads,
    String? rootCertificatesPem,
    String? libraryPath,
  }) async {
    String? pem = rootCertificatesPem;
    if (pem == null && Platform.isAndroid) {
      pem = await _channel.invokeMethod<String>('getSystemRootCertificatesPem');
    }
    return createSync(
      workerThreads: workerThreads,
      rootCertificatesPem: pem,
      libraryPath: libraryPath,
    );
  }

  static ZzuRuntime createSync({
    int? workerThreads,
    String? rootCertificatesPem,
    String? libraryPath,
  }) {
    final bindings = loadLibzzuBindings(libraryPath: libraryPath);
    final options = <String, dynamic>{};
    if (workerThreads != null) {
      options['workerThreads'] = workerThreads;
    }
    if (rootCertificatesPem != null) {
      options['rootCertificatesPem'] = rootCertificatesPem;
    }

    final optionsBytes =
        options.isEmpty ? null : utf8.encode(jsonEncode(options));
    final optionsLen = optionsBytes?.length ?? 0;
    final Pointer<Uint8> optionsPtr = optionsLen > 0
        ? (calloc<Uint8>(optionsLen)
          ..asTypedList(optionsLen).setAll(0, optionsBytes!))
        : nullptr;
    final outRuntimePtr = calloc<Pointer<ZzuRuntimeOpaque>>();
    outRuntimePtr.value = nullptr;
    final outErrorPtr = calloc<Pointer<ZzuBuffer>>();
    outErrorPtr.value = nullptr;

    try {
      final status = bindings.zzuRuntimeNew(
        optionsPtr,
        optionsLen,
        outRuntimePtr,
        outErrorPtr,
      );
      final errorText = readAndFreeBuffer(bindings, outErrorPtr.value);
      if (status != ZzuStatus.ok || outRuntimePtr.value == nullptr) {
        throw ZzuException.fromPayload(status, errorText);
      }
      return ZzuRuntime._(bindings, outRuntimePtr.value);
    } finally {
      if (optionsPtr != nullptr) {
        calloc.free(optionsPtr);
      }
      calloc.free(outRuntimePtr);
      calloc.free(outErrorPtr);
    }
  }

  String version() => bindings.zzuVersion().toDartString();

  int abiVersion() => bindings.zzuAbiVersion();

  void ensureNotDisposed() {
    if (_disposed) {
      throw StateError('ZzuRuntime has been disposed');
    }
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _finalizerFor(bindings).detach(this);
    bindings.zzuRuntimeFree(handle.cast());
  }
}
