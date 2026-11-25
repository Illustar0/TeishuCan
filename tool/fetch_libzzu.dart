import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';

const String libzzuVersion = '0.2.0';
const String _baseUrl =
    'https://github.com/Illustar0/libzzu/releases/download/v$libzzuVersion';

Future<void> main(List<String> args) async {
  final target = _parseTarget(args);
  final rootDir = _findRepoRoot();
  final cacheDir = Directory('${rootDir.path}/build/libzzu/downloads')
    ..createSync(recursive: true);

  final targets = switch (target) {
    'all' => ['ios', 'android', 'windows', 'linux', 'macos'],
    _ => [target],
  };

  final client = HttpClient();
  try {
    for (final item in targets) {
      switch (item) {
        case 'ios':
          await _fetchIos(client, rootDir, cacheDir);
        case 'android':
          await _fetchAndroid(client, rootDir, cacheDir);
        case 'windows':
          await _fetchDesktop(
            client,
            rootDir,
            cacheDir,
            rustTarget: 'x86_64-pc-windows-msvc',
            platformDir: 'windows',
            libraryName: 'zzu.dll',
          );
        case 'linux':
          await _fetchDesktop(
            client,
            rootDir,
            cacheDir,
            rustTarget: 'x86_64-unknown-linux-gnu',
            platformDir: 'linux',
            libraryName: 'libzzu.so',
          );
        case 'macos':
          final arch = await _detectMacArch();
          final rustTarget = arch == 'arm64'
              ? 'aarch64-apple-darwin'
              : 'x86_64-apple-darwin';
          await _fetchDesktop(
            client,
            rootDir,
            cacheDir,
            rustTarget: rustTarget,
            platformDir: 'macos',
            libraryName: 'libzzu.dylib',
          );
        default:
          stderr.writeln(
            'Unsupported target "$item". Use ios, android, windows, macos, linux, or all.',
          );
          exitCode = 64;
          return;
      }
    }
  } finally {
    client.close(force: true);
  }
}

String _parseTarget(List<String> args) {
  for (var i = 0; i < args.length; i++) {
    final arg = args[i];
    if (arg == '--target' && i + 1 < args.length) {
      return args[i + 1];
    }
    if (arg.startsWith('--target=')) {
      return arg.substring('--target='.length);
    }
  }
  if (Platform.isWindows) return 'windows';
  if (Platform.isMacOS) return 'macos';
  if (Platform.isLinux) return 'linux';
  throw ArgumentError('Could not infer default target; pass --target <name>.');
}

Directory _findRepoRoot() {
  var current = Directory.current.absolute;
  while (true) {
    final pubspec = File('${current.path}/pubspec.yaml');
    final toolDir = Directory('${current.path}/tool');
    if (pubspec.existsSync() && toolDir.existsSync()) {
      return current;
    }
    final parent = current.parent;
    if (parent.path == current.path) {
      return Directory.current.absolute;
    }
    current = parent;
  }
}

Future<String> _detectMacArch() async {
  if (!Platform.isMacOS) return 'arm64';
  final result = await Process.run('uname', ['-m']);
  return result.stdout.toString().trim();
}

Future<void> _fetchIos(
  HttpClient client,
  Directory rootDir,
  Directory cacheDir,
) async {
  const archiveName = 'libzzu-ios-xcframework.tar.gz';
  final bytes = await _downloadVerified(client, cacheDir, archiveName);
  final frameworksDir = Directory(
    '${rootDir.path}/packages/libzzu_ffi/ios/Frameworks',
  );
  final xcframeworkDir = Directory('${frameworksDir.path}/libzzu.xcframework');
  if (xcframeworkDir.existsSync()) {
    xcframeworkDir.deleteSync(recursive: true);
  }
  frameworksDir.createSync(recursive: true);

  final archive = _decodeTarGz(bytes);
  const stripPrefix = 'libzzu-ios-xcframework/';
  const simBinaryOld = 'libzzu.xcframework/ios-arm64_x86_64-simulator/libzzu-ios-simulator.a';
  const simBinaryNew = 'libzzu.xcframework/ios-arm64_x86_64-simulator/libzzu.a';
  var extractedCount = 0;
  for (final entry in archive) {
    if (!entry.isFile) continue;
    final normalized = entry.name.replaceAll('\\', '/');
    if (!normalized.startsWith(stripPrefix)) continue;
    var rel = normalized.substring(stripPrefix.length);
    if (!rel.startsWith('libzzu.xcframework/')) continue;
    // CocoaPods rejects static xcframeworks whose slices use differing binary names.
    if (rel == simBinaryOld) {
      rel = simBinaryNew;
    }
    final outFile = File('${frameworksDir.path}/$rel');
    outFile.parent.createSync(recursive: true);
    if (rel == 'libzzu.xcframework/Info.plist') {
      final plistText = utf8
          .decode(entry.content as List<int>)
          .replaceAll('<string>libzzu-ios-simulator.a</string>', '<string>libzzu.a</string>');
      outFile.writeAsStringSync(plistText);
    } else {
      outFile.writeAsBytesSync(entry.content as List<int>);
    }
    extractedCount++;
  }
  if (extractedCount == 0 || !xcframeworkDir.existsSync()) {
    throw StateError('Failed to extract libzzu.xcframework from $archiveName');
  }
  stdout.writeln(
    'Extracted libzzu.xcframework ($extractedCount files) -> ${xcframeworkDir.path}',
  );
}

Future<void> _fetchAndroid(
  HttpClient client,
  Directory rootDir,
  Directory cacheDir,
) async {
  const abiMap = <String, String>{
    'arm64-v8a': 'aarch64-linux-android',
    'armeabi-v7a': 'armv7-linux-androideabi',
    'x86_64': 'x86_64-linux-android',
    'x86': 'i686-linux-android',
  };
  final jniRoot = Directory(
    '${rootDir.path}/packages/libzzu_ffi/android/src/main/jniLibs',
  )..createSync(recursive: true);

  for (final entry in abiMap.entries) {
    final abi = entry.key;
    final rustTarget = entry.value;
    final archiveName = 'libzzu-$rustTarget.tar.gz';
    final bytes = await _downloadVerified(client, cacheDir, archiveName);
    final outFile = File('${jniRoot.path}/$abi/libzzu.so');
    _extractSingleFile(
      bytes,
      expectedSuffix: 'libzzu-$rustTarget/lib/libzzu.so',
      destination: outFile,
    );
    stdout.writeln('Extracted $abi -> ${outFile.path}');
  }
}

Future<void> _fetchDesktop(
  HttpClient client,
  Directory rootDir,
  Directory cacheDir, {
  required String rustTarget,
  required String platformDir,
  required String libraryName,
}) async {
  final archiveName = 'libzzu-$rustTarget.tar.gz';
  final bytes = await _downloadVerified(client, cacheDir, archiveName);
  final outFile = File('${rootDir.path}/build/libzzu/$platformDir/$libraryName');
  _extractSingleFile(
    bytes,
    expectedSuffix: 'libzzu-$rustTarget/lib/$libraryName',
    destination: outFile,
  );
  stdout.writeln('Extracted $rustTarget -> ${outFile.path}');
}

Future<List<int>> _downloadVerified(
  HttpClient client,
  Directory cacheDir,
  String archiveName,
) async {
  final archiveFile = File('${cacheDir.path}/$archiveName');
  final checksumFile = File('${cacheDir.path}/$archiveName.sha256');

  if (!checksumFile.existsSync()) {
    final bytes = await _downloadBytes(
      client,
      Uri.parse('$_baseUrl/$archiveName.sha256'),
    );
    checksumFile.writeAsBytesSync(bytes);
  }
  final expectedSha = utf8
      .decode(checksumFile.readAsBytesSync())
      .trim()
      .split(RegExp(r'\s+'))
      .first
      .toLowerCase();

  List<int> archiveBytes;
  if (archiveFile.existsSync()) {
    archiveBytes = archiveFile.readAsBytesSync();
    final actualSha = sha256.convert(archiveBytes).toString().toLowerCase();
    if (actualSha == expectedSha) {
      return archiveBytes;
    }
    archiveFile.deleteSync();
  }

  stdout.writeln('Downloading $archiveName...');
  archiveBytes = await _downloadBytes(client, Uri.parse('$_baseUrl/$archiveName'));
  final actualSha = sha256.convert(archiveBytes).toString().toLowerCase();
  if (actualSha != expectedSha) {
    throw StateError(
      'SHA-256 mismatch for $archiveName: expected $expectedSha, got $actualSha',
    );
  }
  archiveFile.writeAsBytesSync(archiveBytes);
  return archiveBytes;
}

Future<List<int>> _downloadBytes(HttpClient client, Uri uri) async {
  var current = uri;
  for (var redirects = 0; redirects < 8; redirects++) {
    final req = await client.getUrl(current);
    req.followRedirects = false;
    final res = await req.close();
    if (res.statusCode >= 300 && res.statusCode < 400) {
      final location = res.headers.value(HttpHeaders.locationHeader);
      await res.drain<void>();
      if (location == null) {
        throw HttpException('Redirect without Location header', uri: current);
      }
      current = current.resolve(location);
      continue;
    }
    if (res.statusCode != HttpStatus.ok) {
      await res.drain<void>();
      throw HttpException(
        'Failed to download $current (HTTP ${res.statusCode})',
        uri: current,
      );
    }
    final builder = BytesBuilder(copy: false);
    await for (final chunk in res) {
      builder.add(chunk);
    }
    return builder.takeBytes();
  }
  throw HttpException('Too many redirects downloading $uri', uri: uri);
}

Archive _decodeTarGz(List<int> gzipBytes) {
  final tarBytes = gzip.decode(gzipBytes);
  return TarDecoder().decodeBytes(tarBytes);
}

void _extractSingleFile(
  List<int> gzipBytes, {
  required String expectedSuffix,
  required File destination,
}) {
  final archive = _decodeTarGz(gzipBytes);
  for (final entry in archive) {
    if (!entry.isFile) continue;
    final normalized = entry.name.replaceAll('\\', '/');
    if (normalized == expectedSuffix || normalized.endsWith('/$expectedSuffix')) {
      destination.parent.createSync(recursive: true);
      destination.writeAsBytesSync(entry.content as List<int>);
      return;
    }
  }
  throw StateError('Missing $expectedSuffix in archive');
}
