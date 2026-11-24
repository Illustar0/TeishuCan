import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:libzzu_ffi/libzzu_ffi.dart';

import '../domain/campus_exception.dart';
import '../domain/timetable.dart';
import 'snapshot_cache.dart';
import 'zzu_clients.dart';
import 'zzu_session.dart';

class TimetableRepository extends ChangeNotifier {
  TimetableRepository({
    required this._session,
    required SnapshotStorage snapshots,
  }) : _cache = SnapshotCache<List<TimetableTerm>>(
          storage: snapshots,
          fileName: 'timetable.json',
          initialValue: const <TimetableTerm>[],
          fromJson: (json) => (json as List<dynamic>? ?? const [])
              .map((e) => TimetableTerm.fromJson(e as Map<String, dynamic>))
              .toList(growable: false),
          toJson: (terms) =>
              terms.map((t) => t.toJson()).toList(growable: false),
        ) {
    _cache.addListener(notifyListeners);
    _session.addSessionBinding(_cache.bindSession);
  }

  final ZzuSessionController _session;
  final SnapshotCache<List<TimetableTerm>> _cache;

  bool _isRefreshing = false;
  ZzuClients? _inFlightOwner;
  Future<void>? _inFlightRefresh;

  List<TimetableTerm> get terms => _cache.value;
  bool get isRefreshing => _isRefreshing;

  bool isStaleAt(DateTime now, Duration maxAge) => _cache.isStaleAt(now, maxAge);

  Future<void> refresh() async {
    _isRefreshing = true;
    notifyListeners();
    try {
      await _session.request(CampusFeature.timetable, (clients, signInId) async {
        final existing = _inFlightRefresh;
        if (existing != null && identical(_inFlightOwner, clients)) {
          await existing;
          return;
        }
        final future = _fetchCurrentTerm(clients, signInId);
        _inFlightOwner = clients;
        _inFlightRefresh = future;
        try {
          await future;
        } finally {
          if (identical(_inFlightRefresh, future)) {
            _inFlightRefresh = null;
            _inFlightOwner = null;
          }
        }
      });
    } finally {
      _isRefreshing = false;
      notifyListeners();
    }
  }

  Future<void> _fetchCurrentTerm(ZzuClients clients, String signInId) async {
    final refreshVersion = _cache.nextRefreshVersion();
    final eas = await clients.eas();
    final current = await eas.currentSemester();
    final results = await Future.wait<Object>(<Future<Object>>[
      eas.semesters(),
      eas.timetable(semesterId: current.id),
    ]);
    final listedSemesters = results[0] as List<Semester>;
    final timetable = results[1] as Timetable;

    final byId = <int, Semester>{};
    for (final s in listedSemesters) {
      byId[s.id] = s;
    }
    byId[current.id] = current;
    final semesters = byId.values.toList()
      ..sort((a, b) => a.startDate.compareTo(b.startDate));

    await _cache.update(signInId, refreshVersion, (existingTerms) {
      final loaded = <String, TimetableTerm>{
        for (final t in existingTerms) t.id: t,
      };
      return semesters.map((semester) {
        if (semester.id == current.id) {
          return _semesterToTerm(timetable.semester, timetable: timetable);
        }
        return loaded[semester.id.toString()] ?? _semesterToTerm(semester);
      }).toList(growable: false);
    });
  }

  Future<void> loadTerm(String termId) =>
      _session.request(CampusFeature.timetable, (clients, signInId) async {
        final eas = await clients.eas();
        final timetable = await eas.timetable(semesterId: int.parse(termId));
        await _cache.amend(signInId, (terms) {
          return terms
              .map(
                (t) => t.id == termId
                    ? _semesterToTerm(timetable.semester, timetable: timetable)
                    : t,
              )
              .toList(growable: false);
        });
      });

  @override
  void dispose() {
    _cache.removeListener(notifyListeners);
    _cache.dispose();
    super.dispose();
  }
}

TimetableTerm _semesterToTerm(
  Semester semester, {
  Timetable? timetable,
}) {
  final start = semester.startDate;
  final offset = semester.weekStartOnSunday
      ? (start.weekday % 7)
      : (start.weekday - 1);
  final firstWeekStart = start.addDays(-offset);
  final numbers = timetable?.weekIndices() ?? semester.weekIndices;

  return TimetableTerm(
    id: semester.id.toString(),
    schoolYear: semester.schoolYear,
    number: semester.nameZh.split('-').last,
    startDate: semester.startDate,
    endDate: semester.endDate,
    isLoaded: timetable != null,
    weeks: numbers.map((number) {
      final range = semester.weekDateRange(number);
      final weekStart = range?.start ?? firstWeekStart.addDays((number - 1) * 7);
      final dates = List<ShanghaiDate>.generate(
        7,
        (index) => weekStart.addDays(index),
        growable: false,
      );
      final entries = timetable == null
          ? const <TimetableEntry>[]
          : timetable
              .weekSessions(number)
              .map(_sessionViewToEntry)
              .toList(growable: false);
      return TimetableWeek(number: number, dates: dates, entries: entries);
    }).toList(growable: false),
  );
}

TimetableEntry _sessionViewToEntry(SessionView view) {
  final s = view.session;
  final teachers = s.teachers
      .map((t) => t.nameZh)
      .where((name) => name.trim().isNotEmpty)
      .join('、');
  return TimetableEntry(
    day: s.weekday,
    date: s.date,
    startPeriod: s.startUnit,
    startHour: s.startTime.hour,
    startMinute: s.startTime.minute,
    endHour: s.endTime.hour,
    endMinute: s.endTime.minute,
    title: view.displayTitle,
    teacher: teachers,
    location: s.room?.nameZh ?? s.customPlace,
    periodSpan: s.endUnit - s.startUnit + 1,
  );
}
