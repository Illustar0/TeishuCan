import 'package:libzzu_ffi/libzzu_ffi.dart';

class TimetableEntry {
  const TimetableEntry({
    required this.day,
    required this.date,
    required this.startPeriod,
    required this.startHour,
    required this.startMinute,
    required this.endHour,
    required this.endMinute,
    required this.title,
    required this.teacher,
    this.location,
    this.periodSpan = 1,
  });

  factory TimetableEntry.fromJson(Map<String, dynamic> json) {
    final startParts = (json['startTime'] as String).split(':');
    final endParts = (json['endTime'] as String).split(':');
    return TimetableEntry(
      day: (json['day'] as num).toInt(),
      date: parseLocalDate(json['date'] as String),
      startPeriod: (json['startPeriod'] as num).toInt(),
      startHour: int.parse(startParts[0]),
      startMinute: int.parse(startParts[1]),
      endHour: int.parse(endParts[0]),
      endMinute: int.parse(endParts[1]),
      title: json['title'] as String? ?? '',
      teacher: json['teacher'] as String? ?? '',
      location: json['location'] as String?,
      periodSpan: (json['periodSpan'] as num?)?.toInt() ?? 1,
    );
  }

  /// ISO weekday 1 (Monday) .. 7 (Sunday).
  final int day;
  final ShanghaiDate date;
  final int startPeriod;
  final int startHour;
  final int startMinute;
  final int endHour;
  final int endMinute;
  final String title;
  final String teacher;
  final String? location;
  final int periodSpan;

  String get startTimeText =>
      '$startHour:${startMinute.toString().padLeft(2, '0')}';

  String get endTimeText =>
      '$endHour:${endMinute.toString().padLeft(2, '0')}';

  ShanghaiDateTime get startsAt => ShanghaiDateTime(
        year: date.year,
        month: date.month,
        day: date.day,
        hour: startHour,
        minute: startMinute,
      );

  ClassPeriods get periods => ClassPeriods(
        startPeriod: startPeriod,
        periodSpan: periodSpan,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'day': day,
        'date': date.toIsoString(),
        'startPeriod': startPeriod,
        'startTime': '${startHour.toString().padLeft(2, '0')}:'
            '${startMinute.toString().padLeft(2, '0')}',
        'endTime': '${endHour.toString().padLeft(2, '0')}:'
            '${endMinute.toString().padLeft(2, '0')}',
        'title': title,
        'teacher': teacher,
        if (location != null) 'location': location,
        'periodSpan': periodSpan,
      };
}

class TimetableWeek {
  const TimetableWeek({
    required this.number,
    required this.dates,
    this.entries = const <TimetableEntry>[],
  });

  factory TimetableWeek.fromJson(Map<String, dynamic> json) => TimetableWeek(
        number: (json['number'] as num).toInt(),
        dates: (json['dates'] as List<dynamic>? ?? const [])
            .map((e) => parseLocalDate(e as String))
            .toList(growable: false),
        entries: (json['entries'] as List<dynamic>? ?? const [])
            .map((e) => TimetableEntry.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );

  final int number;
  final List<ShanghaiDate> dates;
  final List<TimetableEntry> entries;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'number': number,
        'dates': dates.map((d) => d.toIsoString()).toList(growable: false),
        'entries': entries.map((e) => e.toJson()).toList(growable: false),
      };
}

class TimetableTerm {
  const TimetableTerm({
    required this.id,
    required this.schoolYear,
    required this.number,
    required this.startDate,
    required this.endDate,
    required this.weeks,
    this.isLoaded = false,
  });

  factory TimetableTerm.fromJson(Map<String, dynamic> json) => TimetableTerm(
        id: json['id'] as String,
        schoolYear: json['schoolYear'] as String? ?? '',
        number: json['number'] as String? ?? '',
        startDate: parseLocalDate(json['startDate'] as String),
        endDate: parseLocalDate(json['endDate'] as String),
        weeks: (json['weeks'] as List<dynamic>? ?? const [])
            .map((e) => TimetableWeek.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
        isLoaded: json['isLoaded'] as bool? ?? false,
      );

  final String id;
  final String schoolYear;
  final String number;
  final ShanghaiDate startDate;
  final ShanghaiDate endDate;
  final List<TimetableWeek> weeks;
  final bool isLoaded;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'schoolYear': schoolYear,
        'number': number,
        'startDate': startDate.toIsoString(),
        'endDate': endDate.toIsoString(),
        'weeks': weeks.map((w) => w.toJson()).toList(growable: false),
        'isLoaded': isLoaded,
      };
}

TimetableTerm? defaultTerm(List<TimetableTerm> terms, ShanghaiDate today) {
  if (terms.isEmpty) return null;
  TimetableTerm? latestStarted;
  for (final term in terms) {
    if (term.startDate.compareTo(today) <= 0) {
      if (latestStarted == null ||
          term.startDate.compareTo(latestStarted.startDate) > 0) {
        latestStarted = term;
      }
    }
  }
  return latestStarted ?? terms.first;
}

class ClassPeriods {
  const ClassPeriods({required this.startPeriod, required this.periodSpan});

  final int startPeriod;
  final int periodSpan;

  bool contains(int period) =>
      period >= startPeriod && period < startPeriod + periodSpan;

  @override
  bool operator ==(Object other) =>
      other is ClassPeriods &&
      startPeriod == other.startPeriod &&
      periodSpan == other.periodSpan;

  @override
  int get hashCode => Object.hash(startPeriod, periodSpan);
}

sealed class UpcomingClass {
  const UpcomingClass();
}

final class UpcomingNotFetched extends UpcomingClass {
  const UpcomingNotFetched();
}

final class UpcomingNone extends UpcomingClass {
  const UpcomingNone();
}

final class NextClass extends UpcomingClass {
  const NextClass({
    required this.name,
    required this.location,
    required this.startHour,
    required this.startMinute,
    required this.periods,
    required this.dayClasses,
    required this.isTomorrow,
  });

  final String name;
  final String location;
  final int startHour;
  final int startMinute;
  final ClassPeriods periods;
  final List<ClassPeriods> dayClasses;
  final bool isTomorrow;
}

UpcomingClass upcomingClassAfter(
  List<TimetableTerm> terms,
  ShanghaiDateTime now,
) {
  final today = now.date;
  final tomorrow = today.addDays(1);
  final days = <ShanghaiDate>{today, tomorrow};
  final termsOfDays = terms
      .where(
        (term) => term.weeks.any((w) => w.dates.any(days.contains)),
      )
      .toList(growable: false);
  if (terms.isEmpty || termsOfDays.any((t) => !t.isLoaded)) {
    return const UpcomingNotFetched();
  }

  final entries = <TimetableEntry>[
    for (final term in termsOfDays)
      for (final week in term.weeks) ...week.entries,
  ];

  TimetableEntry? upcoming;
  for (final entry in entries) {
    if (!days.contains(entry.date)) continue;
    if (entry.startsAt.compareTo(now) <= 0) continue;
    if (upcoming == null || entry.startsAt.compareTo(upcoming.startsAt) < 0) {
      upcoming = entry;
    }
  }

  if (upcoming == null) {
    return const UpcomingNone();
  }

  return NextClass(
    name: upcoming.title,
    location: upcoming.location ?? '',
    startHour: upcoming.startHour,
    startMinute: upcoming.startMinute,
    periods: upcoming.periods,
    dayClasses: entries
        .where((e) => e.date == upcoming!.date)
        .map((e) => e.periods)
        .toList(growable: false),
    isTomorrow: upcoming.date == tomorrow,
  );
}
