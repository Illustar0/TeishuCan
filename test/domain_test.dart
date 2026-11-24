import 'package:flutter_test/flutter_test.dart';
import 'package:libzzu_ffi/libzzu_ffi.dart';
import 'package:teishucan/data/electricity_repository.dart';
import 'package:teishucan/domain/campus_exception.dart';
import 'package:teishucan/domain/campus_service.dart';
import 'package:teishucan/domain/electricity.dart';
import 'package:teishucan/domain/theme_settings.dart';
import 'package:teishucan/domain/timetable.dart';
import 'package:teishucan/l10n/app_strings.dart';

void main() {
  group('RFC 9557 Asia/Shanghai time parsing', () {
    test('strips zone suffix and preserves UTC+8 wall-clock fields', () {
      final parsed =
          parseRfc9557ToShanghai('2026-10-26T08:00:00.123456+08:00[Asia/Shanghai]');
      expect(parsed.year, 2026);
      expect(parsed.month, 10);
      expect(parsed.day, 26);
      expect(parsed.hour, 8);
      expect(parsed.minute, 0);
      expect(parsed.second, 0);
    });
  });

  group('Timetable domain helpers', () {
    TimetableTerm buildTerm({
      required String id,
      required ShanghaiDate start,
      required ShanghaiDate end,
      bool isLoaded = true,
      List<TimetableEntry> week1Entries = const [],
    }) {
      return TimetableTerm(
        id: id,
        schoolYear: '2026-2027',
        number: '1',
        startDate: start,
        endDate: end,
        isLoaded: isLoaded,
        weeks: [
          TimetableWeek(
            number: 1,
            dates: List<ShanghaiDate>.generate(7, (i) => start.addDays(i)),
            entries: week1Entries,
          ),
        ],
      );
    }

    test('defaultTerm picks latest already-started term', () {
      final oldTerm = buildTerm(
        id: '1',
        start: const ShanghaiDate(2026, 2, 16),
        end: const ShanghaiDate(2026, 7, 10),
      );
      final currentTerm = buildTerm(
        id: '2',
        start: const ShanghaiDate(2026, 10, 5),
        end: const ShanghaiDate(2027, 1, 24),
      );
      final futureTerm = buildTerm(
        id: '3',
        start: const ShanghaiDate(2027, 2, 22),
        end: const ShanghaiDate(2027, 7, 11),
      );

      final picked = defaultTerm(
        [oldTerm, currentTerm, futureTerm],
        const ShanghaiDate(2026, 11, 1),
      );
      expect(picked?.id, '2');
    });

    test('upcomingClassAfter finds today and tomorrow classes accurately', () {
      const today = ShanghaiDate(2026, 10, 5);
      final tomorrow = today.addDays(1);
      final term = buildTerm(
        id: '2',
        start: today,
        end: const ShanghaiDate(2027, 1, 24),
        week1Entries: [
          TimetableEntry(
            day: 1,
            date: today,
            startPeriod: 1,
            startHour: 8,
            startMinute: 0,
            endHour: 9,
            endMinute: 40,
            title: '早八课程',
            teacher: '老师A',
            location: '101',
            periodSpan: 2,
          ),
          TimetableEntry(
            day: 2,
            date: tomorrow,
            startPeriod: 5,
            startHour: 14,
            startMinute: 30,
            endHour: 16,
            endMinute: 10,
            title: '明天下午课',
            teacher: '老师B',
            location: '202',
            periodSpan: 2,
          ),
        ],
      );

      final morning = upcomingClassAfter(
        [term],
        const ShanghaiDateTime(year: 2026, month: 10, day: 5, hour: 7, minute: 30),
      );
      expect(morning, isA<NextClass>());
      expect((morning as NextClass).name, '早八课程');
      expect(morning.isTomorrow, isFalse);

      final afternoon = upcomingClassAfter(
        [term],
        const ShanghaiDateTime(year: 2026, month: 10, day: 5, hour: 10, minute: 0),
      );
      expect(afternoon, isA<NextClass>());
      expect((afternoon as NextClass).name, '明天下午课');
      expect(afternoon.isTomorrow, isTrue);
    });
  });

  group('Electricity domain & failure classification', () {
    test('locationParent builds area, building, and level strings', () {
      const area = MeterOption(id: '1', name: 'A区');
      const building = MeterOption(id: '2', name: '1号楼');
      const floor = MeterOption(id: '3', name: '3层');
      expect(locationParent([]), '');
      expect(locationParent([area]), '1');
      expect(locationParent([area, building]), '1-2');
      expect(locationParent([area, building, floor]), '1-2--3');
    });

    test('totalKilowattHours returns null when empty or missing readings', () {
      const meter = ElectricityMeter(
        id: 'm1',
        name: '空调',
        location: MeterLocation(meterId: '1-2--3-301', path: 'A / 1 / 3 / 301'),
      );
      expect(totalKilowattHours(const []), isNull);
      expect(
        totalKilowattHours([const MeterReading(meter: meter, kilowattHours: null)]),
        isNull,
      );
      expect(
        totalKilowattHours([
          const MeterReading(meter: meter, kilowattHours: 12.5),
          const MeterReading(meter: meter, kilowattHours: 7.5),
        ]),
        20.0,
      );
    });

    test('classifyRechargeFailure distinguishes Rejected vs Unconfirmed', () {
      const rejected = CampusRequestException(
        CampusFeature.electricity,
        reason: '支付密码错误',
        cause: ZzuRemoteException(status: 6, message: '支付密码错误'),
      );
      expect(
        classifyRechargeFailure(rejected),
        isA<MeterRechargeRejected>()
            .having((r) => r.reason, 'reason', '支付密码错误'),
      );

      const timeout = CampusRequestException(
        CampusFeature.electricity,
        reason: 'timeout',
        cause: ZzuTimeoutException(status: 12, message: 'timeout'),
      );
      expect(classifyRechargeFailure(timeout), isA<MeterRechargeUnconfirmed>());

      const decodeError = CampusRequestException(
        CampusFeature.electricity,
        reason: 'decode',
        cause: ZzuDecodeException(status: 4, message: 'decode'),
      );
      expect(
        classifyRechargeFailure(decodeError),
        isA<MeterRechargeUnconfirmed>(),
      );
    });
  });

  group('CampusService search', () {
    test('matches across Chinese and Japanese strings', () {
      final zhStrings = AppStrings.resolve(AppLanguage.chinese, null);
      final hits = searchCampusServices('成绩', zhStrings);
      expect(
        hits.whereType<CampusServiceHit>().map((h) => h.service),
        contains(CampusService.grades),
      );

      final jaStrings = AppStrings.resolve(AppLanguage.japanese, null);
      final jaHits = searchCampusServices('図書館', jaStrings);
      expect(
        jaHits.whereType<CampusServiceHit>().map((h) => h.service),
        containsAll([
          CampusService.library,
          CampusService.libraryReservation,
        ]),
      );
    });
  });
}
