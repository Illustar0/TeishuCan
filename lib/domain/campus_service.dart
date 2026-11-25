import '../l10n/app_strings.dart';

enum CampusService {
  library,
  libraryReservation,
  campusCard,
  mail,
  leave,
  presidentMailbox,
  studentStatus,
  programCompletion,
  academicWarnings,
  exams,
  grades,
  timetable,
  courseOfferings,
  schoolCalendar,
  availableClassrooms,
  classroomTimetable,
}

class CampusServiceGroup {
  const CampusServiceGroup({
    required this.titleSelector,
    required this.services,
  });

  final String Function(AppStrings) titleSelector;
  final List<CampusService> services;
}

final List<CampusServiceGroup> mainServiceGroups = <CampusServiceGroup>[
  CampusServiceGroup(
    titleSelector: (s) => s.servicesLibrary,
    services: const <CampusService>[
      CampusService.library,
      CampusService.libraryReservation,
    ],
  ),
  CampusServiceGroup(
    titleSelector: (s) => s.servicesStudentAffairs,
    services: const <CampusService>[
      CampusService.campusCard,
      CampusService.mail,
      CampusService.leave,
      CampusService.presidentMailbox,
    ],
  ),
];

final List<CampusServiceGroup> undergradEasGroups = <CampusServiceGroup>[
  CampusServiceGroup(
    titleSelector: (s) => s.servicesEasRecords,
    services: const <CampusService>[
      CampusService.studentStatus,
      CampusService.programCompletion,
      CampusService.academicWarnings,
    ],
  ),
  CampusServiceGroup(
    titleSelector: (s) => s.servicesEasExamsAndGrades,
    services: const <CampusService>[
      CampusService.exams,
      CampusService.grades,
    ],
  ),
  CampusServiceGroup(
    titleSelector: (s) => s.servicesEasSchedule,
    services: const <CampusService>[
      CampusService.timetable,
      CampusService.courseOfferings,
      CampusService.schoolCalendar,
      CampusService.availableClassrooms,
      CampusService.classroomTimetable,
    ],
  ),
];

sealed class ServiceSearchHit {
  const ServiceSearchHit();
}

final class UndergradEasHit extends ServiceSearchHit {
  const UndergradEasHit();
}

final class CampusNetworkHit extends ServiceSearchHit {
  const CampusNetworkHit();
}

final class CampusServiceHit extends ServiceSearchHit {
  const CampusServiceHit(this.service);
  final CampusService service;
}

bool matchesServiceQuery(String query, List<String> candidates) {
  final tokens = query
      .trim()
      .toLowerCase()
      .split(RegExp(r'\s+'))
      .where((t) => t.isNotEmpty)
      .toList(growable: false);
  if (tokens.isEmpty) return true;
  final haystack = candidates.join('\n').toLowerCase();
  return tokens.every(haystack.contains);
}

List<ServiceSearchHit> searchCampusServices(String query, AppStrings strings) {
  final hits = <ServiceSearchHit>[];
  if (matchesServiceQuery(query, <String>[
    strings.serviceCampusNetwork,
    strings.serviceCampusNetworkSummary,
    strings.servicesNetwork,
  ])) {
    hits.add(const CampusNetworkHit());
  }
  if (matchesServiceQuery(query, <String>[
    strings.serviceUndergradEas,
    strings.serviceUndergradEasSummary,
    strings.servicesAcademic,
  ])) {
    hits.add(const UndergradEasHit());
  }
  for (final group in <CampusServiceGroup>[
    ...mainServiceGroups,
    ...undergradEasGroups,
  ]) {
    final groupTitle = group.titleSelector(strings);
    for (final service in group.services) {
      if (matchesServiceQuery(query, <String>[
        strings.serviceTitle(service),
        strings.serviceSummary(service),
        groupTitle,
      ])) {
        hits.add(CampusServiceHit(service));
      }
    }
  }
  return hits;
}
