import 'dart:ffi';
import 'dart:io';

import 'package:ffi/ffi.dart';

import 'bindings.dart';

const int expectedAbiMajor = 2;
const int expectedAbiMinor = 0;

LibzzuBindings? _cachedBindings;
String? _cachedLibraryPath;

LibzzuBindings loadLibzzuBindings({String? libraryPath}) {
  final overridePath =
      libraryPath ?? Platform.environment['LIBZZU_LIBRARY_PATH'];
  if (_cachedBindings != null && _cachedLibraryPath == overridePath) {
    return _cachedBindings!;
  }

  final library = _openLibrary(overridePath);
  final bindings = LibzzuBindings(library);
  _verifyAbi(bindings);
  _cachedBindings = bindings;
  _cachedLibraryPath = overridePath;
  return bindings;
}

DynamicLibrary _openLibrary(String? overridePath) {
  if (overridePath != null && overridePath.isNotEmpty) {
    return DynamicLibrary.open(overridePath);
  }
  if (Platform.isIOS) {
    final processLib = DynamicLibrary.process();
    if (processLib.providesSymbol('zzu_abi_version')) {
      return processLib;
    }
    return DynamicLibrary.open('libzzu_ffi.framework/libzzu_ffi');
  }
  if (Platform.isAndroid || Platform.isLinux) {
    return DynamicLibrary.open('libzzu.so');
  }
  if (Platform.isMacOS) {
    return DynamicLibrary.open('libzzu.dylib');
  }
  if (Platform.isWindows) {
    return DynamicLibrary.open('zzu.dll');
  }
  throw UnsupportedError(
    'libzzu is not supported on ${Platform.operatingSystem}',
  );
}

void _verifyAbi(LibzzuBindings bindings) {
  final packed = bindings.zzuAbiVersion();
  final major = packed >> 16;
  final minor = packed & 0xFFFF;
  if (major != expectedAbiMajor || minor < expectedAbiMinor) {
    final version = bindings.zzuVersion().toDartString();
    throw StateError(
      'Incompatible libzzu $version ABI $major.$minor; '
      'expected $expectedAbiMajor.$expectedAbiMinor+',
    );
  }
}
