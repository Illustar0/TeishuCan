import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:libzzu_ffi/libzzu_ffi.dart';

String _resolveNativeLibraryPath() {
  final fromEnv = Platform.environment['LIBZZU_LIBRARY_PATH'];
  if (fromEnv != null && fromEnv.isNotEmpty) return fromEnv;

  final candidates = Platform.isWindows
      ? <String>[
          'build/libzzu/windows/zzu.dll',
          '../../build/libzzu/windows/zzu.dll',
        ]
      : Platform.isLinux
          ? <String>[
              'build/libzzu/linux/libzzu.so',
              '../../build/libzzu/linux/libzzu.so',
            ]
          : Platform.isMacOS
              ? <String>[
                  'build/libzzu/macos/libzzu.dylib',
                  '../../build/libzzu/macos/libzzu.dylib',
                ]
              : <String>[];

  for (final path in candidates) {
    if (File(path).existsSync()) {
      return File(path).absolute.path;
    }
  }
  throw StateError(
    'Missing native libzzu test library; set LIBZZU_LIBRARY_PATH or run tool/fetch_libzzu.dart',
  );
}

void main() {
  late String libPath;
  late ZzuRuntime runtime;

  setUpAll(() {
    libPath = _resolveNativeLibraryPath();
    runtime = ZzuRuntime.createSync(libraryPath: libPath);
  });

  tearDownAll(() {
    runtime.dispose();
  });

  test('ABI major version is 2 and software version is non-empty', () {
    final packed = runtime.abiVersion();
    expect(packed >> 16, 2);
    expect(runtime.version(), isNotEmpty);
  });

  test('CasClient round-trips deviceId, initial login state, and empty tokens',
      () async {
    final cas = CasClient(
      runtime,
      account: '20260001',
      password: 'test-password',
      deviceId: 'TeishuCan(ffi-test-device)',
    );
    try {
      expect(await cas.isLoggedIn(), isFalse);
      expect(await cas.deviceId(), 'TeishuCan(ffi-test-device)');
      expect(await cas.tokens(), isNull);
    } finally {
      cas.dispose();
    }
  });

  test('parseRoomId accepts valid format and rejects invalid input', () {
    expect(parseRoomId('1-2--3-301', libraryPath: libPath), '1-2--3-301');
    expect(
      () => parseRoomId('invalid-room', libraryPath: libPath),
      throwsA(isA<ZzuInvalidInputException>()),
    );
  });

  test('timetableToCalendar renders valid iCalendar output', () {
    final timetable = Timetable.fromJson(<String, dynamic>{
      'semester': <String, dynamic>{
        'id': 1,
        'code': '2026-2027-1',
        'nameZh': '2026-2027-1',
        'schoolYear': '2026-2027',
        'season': 'autumn',
        'startDate': '2026-10-05',
        'endDate': '2027-01-24',
        'weekStartOnSunday': false,
        'countInTerm': true,
        'weekIndices': [1, 2],
      },
      'classes': <Map<String, dynamic>>[
        <String, dynamic>{
          'id': 10,
          'code': 'CS101-01',
          'origin': 'regular',
          'course': <String, dynamic>{
            'id': 100,
            'code': 'CS101',
            'nameZh': '算法设计与分析',
            'credits': 3.0,
            'periodInfo': <String, dynamic>{'total': 48},
          },
          'teachers': <Map<String, dynamic>>[
            <String, dynamic>{'nameZh': '张老师'},
          ],
          'scheduleState': 'published',
        },
      ],
      'sessions': <Map<String, dynamic>>[
        <String, dynamic>{
          'classId': 10,
          'scheduleGroupId': 1,
          'weekIndex': 1,
          'date': '2026-10-05',
          'weekday': 1,
          'startTime': '2026-10-05T08:00:00+08:00[Asia/Shanghai]',
          'endTime': '2026-10-05T09:40:00+08:00[Asia/Shanghai]',
          'startUnit': 1,
          'endUnit': 2,
          'teachers': <Map<String, dynamic>>[
            <String, dynamic>{'nameZh': '张老师'},
          ],
          'kind': 'theory',
          'customPlace': '北核心楼 101',
        },
      ],
    });

    final ics = timetableToCalendar(timetable, libraryPath: libPath);
    expect(ics, startsWith('BEGIN:VCALENDAR'));
    expect(ics, contains('BEGIN:VEVENT'));
    expect(ics, contains('算法设计与分析'));
    expect(ics, contains('END:VCALENDAR'));
  });
}
