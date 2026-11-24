import 'time.dart';

class Tokens {
  const Tokens({
    required this.userToken,
    required this.refreshToken,
  });

  factory Tokens.fromJson(Map<String, dynamic> json) {
    return Tokens(
      userToken: json['userToken'] as String,
      refreshToken: json['refreshToken'] as String,
    );
  }

  final String userToken;
  final String refreshToken;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'userToken': userToken,
        'refreshToken': refreshToken,
      };
}

class PersonalInfo {
  const PersonalInfo({
    required this.uid,
    required this.name,
    required this.identityTypeName,
    required this.identityTypeId,
    required this.organizationName,
    required this.organizationId,
  });

  factory PersonalInfo.fromJson(Map<String, dynamic> json) {
    return PersonalInfo(
      uid: json['uid'] as String? ?? '',
      name: json['name'] as String? ?? '',
      identityTypeName: json['identityTypeName'] as String? ?? '',
      identityTypeId: json['identityTypeId'] as String? ?? '',
      organizationName: json['organizationName'] as String? ?? '',
      organizationId: json['organizationId'] as String? ?? '',
    );
  }

  final String uid;
  final String name;
  final String identityTypeName;
  final String identityTypeId;
  final String organizationName;
  final String organizationId;
}

class MfaStatus {
  const MfaStatus({
    required this.isRequired,
    required this.canUseSms,
    required this.isVerified,
  });

  factory MfaStatus.fromJson(Map<String, dynamic> json) {
    return MfaStatus(
      isRequired: json['isRequired'] as bool? ?? false,
      canUseSms: json['canUseSms'] as bool? ?? false,
      isVerified: json['isVerified'] as bool? ?? false,
    );
  }

  final bool isRequired;
  final bool canUseSms;
  final bool isVerified;
}

class SmsPrepared {
  const SmsPrepared({this.securePhone});

  factory SmsPrepared.fromJson(Map<String, dynamic> json) {
    return SmsPrepared(securePhone: json['securePhone'] as String?);
  }

  final String? securePhone;
}

class SsoService {
  const SsoService._(this.jsonValue);

  final Object jsonValue;

  static const SsoService email = SsoService._('email');
  static const SsoService library = SsoService._('library');
  static const SsoService libraryReservation =
      SsoService._('library_reservation');
  static const SsoService leave = SsoService._('leave');
  static const SsoService presidentMailbox =
      SsoService._('president_mailbox');
  static const SsoService ecard = SsoService._('ecard');

  static const SsoService undergradStudentStatus =
      SsoService._(<String, String>{'undergrad_eas': 'student_status'});
  static const SsoService undergradProgramCompletion =
      SsoService._(<String, String>{'undergrad_eas': 'program_completion'});
  static const SsoService undergradExams =
      SsoService._(<String, String>{'undergrad_eas': 'exams'});
  static const SsoService undergradAcademicWarnings =
      SsoService._(<String, String>{'undergrad_eas': 'academic_warnings'});
  static const SsoService undergradGrades =
      SsoService._(<String, String>{'undergrad_eas': 'grades'});
  static const SsoService undergradTimetable =
      SsoService._(<String, String>{'undergrad_eas': 'timetable'});
  static const SsoService undergradCourseOfferings =
      SsoService._(<String, String>{'undergrad_eas': 'course_offerings'});
  static const SsoService undergradSchoolCalendar =
      SsoService._(<String, String>{'undergrad_eas': 'school_calendar'});
  static const SsoService undergradAvailableClassrooms =
      SsoService._(<String, String>{'undergrad_eas': 'available_classrooms'});
  static const SsoService undergradClassroomTimetable =
      SsoService._(<String, String>{'undergrad_eas': 'classroom_timetable'});
}

class NamedEntity {
  const NamedEntity({
    required this.id,
    required this.nameZh,
    this.nameEn,
    required this.code,
  });

  factory NamedEntity.fromJson(Map<String, dynamic> json) {
    return NamedEntity(
      id: (json['id'] as num).toInt(),
      nameZh: json['nameZh'] as String? ?? '',
      nameEn: json['nameEn'] as String?,
      code: json['code'] as String? ?? '',
    );
  }

  final int id;
  final String nameZh;
  final String? nameEn;
  final String code;
}

class Semester {
  const Semester({
    required this.id,
    required this.code,
    required this.nameZh,
    this.nameEn,
    required this.schoolYear,
    required this.season,
    required this.startDate,
    required this.endDate,
    required this.weekStartOnSunday,
    required this.countInTerm,
    required this.weekIndices,
  });

  factory Semester.fromJson(Map<String, dynamic> json) {
    return Semester(
      id: (json['id'] as num).toInt(),
      code: json['code'] as String? ?? '',
      nameZh: json['nameZh'] as String? ?? '',
      nameEn: json['nameEn'] as String?,
      schoolYear: json['schoolYear'] as String? ?? '',
      season: json['season'] as String? ?? '',
      startDate: parseLocalDate(json['startDate'] as String),
      endDate: parseLocalDate(json['endDate'] as String),
      weekStartOnSunday: json['weekStartOnSunday'] as bool? ?? false,
      countInTerm: json['countInTerm'] as bool? ?? true,
      weekIndices: (json['weekIndices'] as List<dynamic>? ?? const [])
          .map((e) => (e as num).toInt())
          .toList(growable: false),
    );
  }

  final int id;
  final String code;
  final String nameZh;
  final String? nameEn;
  final String schoolYear;
  final String season;
  final ShanghaiDate startDate;
  final ShanghaiDate endDate;
  final bool weekStartOnSunday;
  final bool countInTerm;
  final List<int> weekIndices;

  int? weekOf(ShanghaiDate date) {
    if (date.compareTo(startDate) < 0 || date.compareTo(endDate) > 0) {
      return null;
    }
    final firstStart = _weekStart(startDate, weekStartOnSunday);
    final dateStart = _weekStart(date, weekStartOnSunday);
    final days =
        dateStart.toUtcMidnight().difference(firstStart.toUtcMidnight()).inDays;
    if (days < 0) return null;
    final index = days ~/ 7;
    if (index >= weekIndices.length) return null;
    return weekIndices[index];
  }

  ({ShanghaiDate start, ShanghaiDate end})? weekDateRange(int weekIndex) {
    final position = weekIndices.indexOf(weekIndex);
    if (position < 0) return null;
    final first =
        _weekStart(startDate, weekStartOnSunday).addDays(position * 7);
    return (start: first, end: first.addDays(6));
  }

  static ShanghaiDate _weekStart(ShanghaiDate date, bool startsOnSunday) {
    final offset = startsOnSunday ? (date.weekday % 7) : (date.weekday - 1);
    return date.addDays(-offset);
  }
}

class PeriodInfo {
  const PeriodInfo({
    required this.total,
    this.weeks,
    this.periodsPerWeek,
    this.theory,
    this.experiment,
    this.practice,
    this.design,
    this.machine,
    this.test,
  });

  factory PeriodInfo.fromJson(Map<String, dynamic> json) {
    return PeriodInfo(
      total: (json['total'] as num?)?.toInt() ?? 0,
      weeks: (json['weeks'] as num?)?.toInt(),
      periodsPerWeek: (json['periodsPerWeek'] as num?)?.toInt(),
      theory: (json['theory'] as num?)?.toInt(),
      experiment: (json['experiment'] as num?)?.toInt(),
      practice: (json['practice'] as num?)?.toInt(),
      design: (json['design'] as num?)?.toInt(),
      machine: (json['machine'] as num?)?.toInt(),
      test: (json['test'] as num?)?.toInt(),
    );
  }

  final int total;
  final int? weeks;
  final int? periodsPerWeek;
  final int? theory;
  final int? experiment;
  final int? practice;
  final int? design;
  final int? machine;
  final int? test;
}

class Course {
  const Course({
    required this.id,
    required this.code,
    required this.nameZh,
    this.nameEn,
    this.credits,
    required this.periodInfo,
  });

  factory Course.fromJson(Map<String, dynamic> json) {
    return Course(
      id: (json['id'] as num).toInt(),
      code: json['code'] as String? ?? '',
      nameZh: json['nameZh'] as String? ?? '',
      nameEn: json['nameEn'] as String?,
      credits: (json['credits'] as num?)?.toDouble(),
      periodInfo: PeriodInfo.fromJson(
        json['periodInfo'] as Map<String, dynamic>? ?? const {},
      ),
    );
  }

  final int id;
  final String code;
  final String nameZh;
  final String? nameEn;
  final double? credits;
  final PeriodInfo periodInfo;
}

class TeacherName {
  const TeacherName({required this.nameZh, this.nameEn});

  factory TeacherName.fromJson(Map<String, dynamic> json) {
    return TeacherName(
      nameZh: json['nameZh'] as String? ?? '',
      nameEn: json['nameEn'] as String?,
    );
  }

  final String nameZh;
  final String? nameEn;
}

enum ClassOrigin { regular, standaloneExperiment }

class TeachingClass {
  const TeachingClass({
    required this.id,
    required this.code,
    required this.origin,
    required this.course,
    this.courseType,
    this.cultivateType,
    this.department,
    this.campus,
    required this.teachers,
    this.studentCount,
    required this.scheduleState,
    this.scheduleText,
    this.scheduleTextEn,
    this.remark,
  });

  factory TeachingClass.fromJson(Map<String, dynamic> json) {
    final originRaw = json['origin'] as String? ?? 'regular';
    return TeachingClass(
      id: (json['id'] as num).toInt(),
      code: json['code'] as String? ?? '',
      origin: originRaw == 'standalone_experiment'
          ? ClassOrigin.standaloneExperiment
          : ClassOrigin.regular,
      course: Course.fromJson(json['course'] as Map<String, dynamic>),
      courseType: _optEntity(json['courseType']),
      cultivateType: _optEntity(json['cultivateType']),
      department: _optEntity(json['department']),
      campus: _optEntity(json['campus']),
      teachers: (json['teachers'] as List<dynamic>? ?? const [])
          .map((e) => TeacherName.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
      studentCount: (json['studentCount'] as num?)?.toInt(),
      scheduleState: json['scheduleState'] as String? ?? 'published',
      scheduleText: json['scheduleText'] as String?,
      scheduleTextEn: json['scheduleTextEn'] as String?,
      remark: json['remark'] as String?,
    );
  }

  static NamedEntity? _optEntity(Object? raw) =>
      raw is Map<String, dynamic> ? NamedEntity.fromJson(raw) : null;

  final int id;
  final String code;
  final ClassOrigin origin;
  final Course course;
  final NamedEntity? courseType;
  final NamedEntity? cultivateType;
  final NamedEntity? department;
  final NamedEntity? campus;
  final List<TeacherName> teachers;
  final int? studentCount;
  final String scheduleState;
  final String? scheduleText;
  final String? scheduleTextEn;
  final String? remark;
}

class SessionTeacher {
  const SessionTeacher({
    this.id,
    this.personId,
    required this.nameZh,
    this.nameEn,
  });

  factory SessionTeacher.fromJson(Map<String, dynamic> json) {
    return SessionTeacher(
      id: (json['id'] as num?)?.toInt(),
      personId: (json['personId'] as num?)?.toInt(),
      nameZh: json['nameZh'] as String? ?? '',
      nameEn: json['nameEn'] as String?,
    );
  }

  final int? id;
  final int? personId;
  final String nameZh;
  final String? nameEn;
}

class ExperimentProject {
  const ExperimentProject({
    required this.id,
    required this.code,
    required this.nameZh,
    this.totalPeriods,
  });

  factory ExperimentProject.fromJson(Map<String, dynamic> json) {
    return ExperimentProject(
      id: (json['id'] as num).toInt(),
      code: json['code'] as String? ?? '',
      nameZh: json['nameZh'] as String? ?? '',
      totalPeriods: (json['totalPeriods'] as num?)?.toInt(),
    );
  }

  final int id;
  final String code;
  final String nameZh;
  final int? totalPeriods;
}

class Room {
  const Room({
    required this.id,
    required this.nameZh,
    this.nameEn,
    required this.building,
    required this.campus,
    this.seatNumber,
  });

  factory Room.fromJson(Map<String, dynamic> json) {
    return Room(
      id: (json['id'] as num).toInt(),
      nameZh: json['nameZh'] as String? ?? '',
      nameEn: json['nameEn'] as String?,
      building: NamedEntity.fromJson(json['building'] as Map<String, dynamic>),
      campus: NamedEntity.fromJson(json['campus'] as Map<String, dynamic>),
      seatNumber: json['seatNumber'] as String?,
    );
  }

  final int id;
  final String nameZh;
  final String? nameEn;
  final NamedEntity building;
  final NamedEntity campus;
  final String? seatNumber;
}

class ClassSession {
  const ClassSession({
    required this.classId,
    required this.scheduleGroupId,
    required this.weekIndex,
    required this.date,
    this.originalDate,
    required this.weekday,
    required this.startTime,
    required this.endTime,
    required this.startUnit,
    required this.endUnit,
    this.startUnitNameZh,
    this.endUnitNameZh,
    required this.teachers,
    this.room,
    this.customPlace,
    required this.kind,
    this.state,
    this.periods,
    this.experimentProject,
  });

  factory ClassSession.fromJson(Map<String, dynamic> json) {
    final origDate = json['originalDate'] as String?;
    final roomRaw = json['room'];
    final expRaw = json['experimentProject'];
    return ClassSession(
      classId: (json['classId'] as num).toInt(),
      scheduleGroupId: (json['scheduleGroupId'] as num).toInt(),
      weekIndex: (json['weekIndex'] as num).toInt(),
      date: parseLocalDate(json['date'] as String),
      originalDate: origDate == null ? null : parseLocalDate(origDate),
      weekday: (json['weekday'] as num).toInt(),
      startTime: parseRfc9557ToShanghai(json['startTime'] as String),
      endTime: parseRfc9557ToShanghai(json['endTime'] as String),
      startUnit: (json['startUnit'] as num).toInt(),
      endUnit: (json['endUnit'] as num).toInt(),
      startUnitNameZh: json['startUnitNameZh'] as String?,
      endUnitNameZh: json['endUnitNameZh'] as String?,
      teachers: (json['teachers'] as List<dynamic>? ?? const [])
          .map((e) => SessionTeacher.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
      room: roomRaw is Map<String, dynamic> ? Room.fromJson(roomRaw) : null,
      customPlace: json['customPlace'] as String?,
      kind: json['kind'] as String? ?? 'theory',
      state: json['state'] as String?,
      periods: (json['periods'] as num?)?.toInt(),
      experimentProject: expRaw is Map<String, dynamic>
          ? ExperimentProject.fromJson(expRaw)
          : null,
    );
  }

  final int classId;
  final int scheduleGroupId;
  final int weekIndex;
  final ShanghaiDate date;
  final ShanghaiDate? originalDate;
  final int weekday;
  final ShanghaiDateTime startTime;
  final ShanghaiDateTime endTime;
  final int startUnit;
  final int endUnit;
  final String? startUnitNameZh;
  final String? endUnitNameZh;
  final List<SessionTeacher> teachers;
  final Room? room;
  final String? customPlace;
  final String kind;
  final String? state;
  final int? periods;
  final ExperimentProject? experimentProject;
}

class SessionView {
  const SessionView({
    required this.teachingClass,
    required this.session,
  });

  final TeachingClass teachingClass;
  final ClassSession session;

  Course get course => teachingClass.course;

  String get displayTitle {
    final project = session.experimentProject;
    if (project != null && project.nameZh != course.nameZh) {
      return '${course.nameZh} - ${project.nameZh}';
    }
    return course.nameZh;
  }
}

class Timetable {
  const Timetable({
    required this.semester,
    required this.classes,
    required this.sessions,
    required this.rawJson,
  });

  factory Timetable.fromJson(Map<String, dynamic> json) {
    return Timetable(
      semester: Semester.fromJson(json['semester'] as Map<String, dynamic>),
      classes: (json['classes'] as List<dynamic>? ?? const [])
          .map((e) => TeachingClass.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
      sessions: (json['sessions'] as List<dynamic>? ?? const [])
          .map((e) => ClassSession.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
      rawJson: json,
    );
  }

  final Semester semester;
  final List<TeachingClass> classes;
  final List<ClassSession> sessions;
  final Map<String, dynamic> rawJson;

  List<int> weekIndices() {
    final set = <int>{...semester.weekIndices};
    for (final session in sessions) {
      set.add(session.weekIndex);
    }
    final list = set.toList()..sort();
    return list;
  }

  List<SessionView> weekSessions(int weekIndex) {
    final classById = <int, TeachingClass>{
      for (final c in classes) c.id: c,
    };
    final result = <SessionView>[];
    for (final session in sessions) {
      if (session.weekIndex != weekIndex) continue;
      final tc = classById[session.classId];
      if (tc != null) {
        result.add(SessionView(teachingClass: tc, session: session));
      }
    }
    return result;
  }
}

class CalendarExportOptions {
  const CalendarExportOptions({
    this.weeks,
    this.classIds,
    this.startDate,
    this.endDate,
    this.alarmMinutesBefore,
    this.calendarName,
  });

  final List<int>? weeks;
  final List<int>? classIds;
  final ShanghaiDate? startDate;
  final ShanghaiDate? endDate;
  final List<int>? alarmMinutesBefore;
  final String? calendarName;

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      if (weeks != null) 'weeks': weeks,
      if (classIds != null) 'classIds': classIds,
      if (startDate != null) 'startDate': startDate!.toIsoString(),
      if (endDate != null) 'endDate': endDate!.toIsoString(),
      if (alarmMinutesBefore != null) 'alarmMinutesBefore': alarmMinutesBefore,
      if (calendarName != null) 'calendarName': calendarName,
    };
  }
}

class Campus {
  const Campus({
    required this.id,
    required this.code,
    required this.nameZh,
    this.nameEn,
    this.remark,
    required this.enabled,
  });

  factory Campus.fromJson(Map<String, dynamic> json) => Campus(
        id: (json['id'] as num).toInt(),
        code: json['code'] as String? ?? '',
        nameZh: json['nameZh'] as String? ?? '',
        nameEn: json['nameEn'] as String?,
        remark: json['remark'] as String?,
        enabled: json['enabled'] as bool? ?? true,
      );

  final int id;
  final String code;
  final String nameZh;
  final String? nameEn;
  final String? remark;
  final bool enabled;
}

class Building {
  const Building({
    required this.id,
    required this.code,
    required this.nameZh,
    this.nameEn,
    required this.campusId,
    this.remark,
    required this.enabled,
  });

  factory Building.fromJson(Map<String, dynamic> json) => Building(
        id: (json['id'] as num).toInt(),
        code: json['code'] as String? ?? '',
        nameZh: json['nameZh'] as String? ?? '',
        nameEn: json['nameEn'] as String?,
        campusId: (json['campusId'] as num).toInt(),
        remark: json['remark'] as String?,
        enabled: json['enabled'] as bool? ?? true,
      );

  final int id;
  final String code;
  final String nameZh;
  final String? nameEn;
  final int campusId;
  final String? remark;
  final bool enabled;
}

class RoomType {
  const RoomType({
    required this.id,
    required this.code,
    required this.nameZh,
    this.nameEn,
    required this.enabled,
  });

  factory RoomType.fromJson(Map<String, dynamic> json) => RoomType(
        id: (json['id'] as num).toInt(),
        code: json['code'] as String? ?? '',
        nameZh: json['nameZh'] as String? ?? '',
        nameEn: json['nameEn'] as String?,
        enabled: json['enabled'] as bool? ?? true,
      );

  final int id;
  final String code;
  final String nameZh;
  final String? nameEn;
  final bool enabled;
}

class ClassroomCatalog {
  const ClassroomCatalog({
    required this.campuses,
    required this.buildings,
    required this.roomTypes,
    required this.floors,
  });

  factory ClassroomCatalog.fromJson(Map<String, dynamic> json) =>
      ClassroomCatalog(
        campuses: (json['campuses'] as List<dynamic>? ?? const [])
            .map((e) => Campus.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
        buildings: (json['buildings'] as List<dynamic>? ?? const [])
            .map((e) => Building.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
        roomTypes: (json['roomTypes'] as List<dynamic>? ?? const [])
            .map((e) => RoomType.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
        floors: (json['floors'] as List<dynamic>? ?? const [])
            .map((e) => (e as num).toInt())
            .toList(growable: false),
      );

  final List<Campus> campuses;
  final List<Building> buildings;
  final List<RoomType> roomTypes;
  final List<int> floors;
}

class RoomOccupation {
  const RoomOccupation({
    this.activityId,
    required this.activityType,
    required this.activityName,
    this.activityNameEn,
    required this.date,
    required this.startTime,
    required this.endTime,
    required this.teacherName,
    this.teacherNameEn,
  });

  factory RoomOccupation.fromJson(Map<String, dynamic> json) => RoomOccupation(
        activityId: (json['activityId'] as num?)?.toInt(),
        activityType: json['activityType'] as String? ?? '',
        activityName: json['activityName'] as String? ?? '',
        activityNameEn: json['activityNameEn'] as String?,
        date: parseLocalDate(json['date'] as String),
        startTime: parseRfc9557ToShanghai(json['startTime'] as String),
        endTime: parseRfc9557ToShanghai(json['endTime'] as String),
        teacherName: json['teacherName'] as String? ?? '',
        teacherNameEn: json['teacherNameEn'] as String?,
      );

  final int? activityId;
  final String activityType;
  final String activityName;
  final String? activityNameEn;
  final ShanghaiDate date;
  final ShanghaiDateTime startTime;
  final ShanghaiDateTime endTime;
  final String teacherName;
  final String? teacherNameEn;
}

class ClassroomOccupancy {
  const ClassroomOccupancy({
    required this.id,
    required this.code,
    required this.nameZh,
    this.nameEn,
    required this.buildingId,
    required this.campusNameZh,
    required this.occupations,
  });

  factory ClassroomOccupancy.fromJson(Map<String, dynamic> json) =>
      ClassroomOccupancy(
        id: (json['id'] as num).toInt(),
        code: json['code'] as String? ?? '',
        nameZh: json['nameZh'] as String? ?? '',
        nameEn: json['nameEn'] as String?,
        buildingId: (json['buildingId'] as num).toInt(),
        campusNameZh: json['campusNameZh'] as String? ?? '',
        occupations: (json['occupations'] as List<dynamic>? ?? const [])
            .map((e) => RoomOccupation.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );

  final int id;
  final String code;
  final String nameZh;
  final String? nameEn;
  final int buildingId;
  final String campusNameZh;
  final List<RoomOccupation> occupations;
}

class ClassroomPage {
  const ClassroomPage({
    required this.items,
    required this.page,
    required this.pageSize,
    required this.totalItems,
    required this.totalPages,
  });

  factory ClassroomPage.fromJson(Map<String, dynamic> json) => ClassroomPage(
        items: (json['items'] as List<dynamic>? ?? const [])
            .map((e) => ClassroomOccupancy.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
        page: (json['page'] as num).toInt(),
        pageSize: (json['pageSize'] as num).toInt(),
        totalItems: (json['totalItems'] as num).toInt(),
        totalPages: (json['totalPages'] as num).toInt(),
      );

  final List<ClassroomOccupancy> items;
  final int page;
  final int pageSize;
  final int totalItems;
  final int totalPages;
}

class LocationOption {
  const LocationOption({required this.id, required this.name});

  factory LocationOption.fromJson(Map<String, dynamic> json) {
    return LocationOption(
      id: json['id'] as String,
      name: json['name'] as String,
    );
  }

  final String id;
  final String name;
}

class RechargeConfig {
  const RechargeConfig({
    required this.amounts,
    required this.isBrowserPaymentEnabled,
  });

  factory RechargeConfig.fromJson(Map<String, dynamic> json) {
    return RechargeConfig(
      amounts: (json['amounts'] as List<dynamic>? ?? const [])
          .map((e) => (e as num).toInt())
          .toList(growable: false),
      isBrowserPaymentEnabled:
          json['isBrowserPaymentEnabled'] as bool? ?? false,
    );
  }

  final List<int> amounts;
  final bool isBrowserPaymentEnabled;
}

class RechargeOrder {
  const RechargeOrder({
    required this.id,
    required this.amount,
    required this.checkoutUrl,
    required this.rawJson,
  });

  factory RechargeOrder.fromJson(Map<String, dynamic> json) {
    return RechargeOrder(
      id: json['id'] as String,
      amount: (json['amount'] as num).toInt(),
      checkoutUrl: json['checkoutUrl'] as String,
      rawJson: json,
    );
  }

  final String id;
  final int amount;
  final String checkoutUrl;
  final Map<String, dynamic> rawJson;
}

class PaymentChannel {
  const PaymentChannel({
    required this.id,
    required this.name,
    this.accountId,
  });

  factory PaymentChannel.fromJson(Map<String, dynamic> json) {
    return PaymentChannel(
      id: json['id'] as String,
      name: json['name'] as String,
      accountId: json['accountId'] as String?,
    );
  }

  final String id;
  final String name;
  final String? accountId;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'name': name,
        if (accountId != null) 'accountId': accountId,
      };
}

enum PaymentActionKind { redirect, submitForm }

class PaymentAction {
  const PaymentAction({
    required this.kind,
    required this.url,
    this.referrer,
    this.fields = const <({String name, String value})>[],
  });

  factory PaymentAction.fromJson(Map<String, dynamic> json) {
    final kindRaw = json['kind'] as String;
    if (kindRaw == 'redirect') {
      return PaymentAction(
        kind: PaymentActionKind.redirect,
        url: json['url'] as String,
        referrer: json['referrer'] as String?,
      );
    }
    return PaymentAction(
      kind: PaymentActionKind.submitForm,
      url: json['url'] as String,
      fields: (json['fields'] as List<dynamic>? ?? const [])
          .map((e) {
            final map = e as Map<String, dynamic>;
            return (
              name: map['name'] as String,
              value: map['value'] as String,
            );
          })
          .toList(growable: false),
    );
  }

  final PaymentActionKind kind;
  final String url;
  final String? referrer;
  final List<({String name, String value})> fields;
}

class PortalDiscovery {
  const PortalDiscovery({
    required this.authUrl,
    required this.portalServerUrl,
    required this.userIp,
  });

  factory PortalDiscovery.fromJson(Map<String, dynamic> json) {
    return PortalDiscovery(
      authUrl: json['authUrl'] as String,
      portalServerUrl: json['portalServerUrl'] as String,
      userIp: json['userIp'] as String,
    );
  }

  final String authUrl;
  final String portalServerUrl;
  final String userIp;
}

class IspInfo {
  const IspInfo({required this.name, required this.suffix});

  factory IspInfo.fromJson(Map<String, dynamic> json) {
    return IspInfo(
      name: json['name'] as String,
      suffix: json['suffix'] as String,
    );
  }

  final String name;
  final String suffix;
}

class PortalAuth {
  const PortalAuth({
    required this.isSuccess,
    required this.result,
    required this.message,
    this.retCode,
  });

  factory PortalAuth.fromJson(Map<String, dynamic> json) {
    return PortalAuth(
      isSuccess: json['isSuccess'] as bool? ?? false,
      result: (json['result'] as num).toInt(),
      message: json['message'] as String? ?? '',
      retCode: (json['retCode'] as num?)?.toInt(),
    );
  }

  final bool isSuccess;
  final int result;
  final String message;
  final int? retCode;
}

class OnlineDevice {
  const OnlineDevice({
    required this.brasid,
    required this.downFlow,
    required this.hostName,
    required this.ip,
    required this.loginTime,
    required this.mac,
    required this.sessionId,
    required this.terminalType,
    required this.upFlow,
    required this.useTime,
    required this.userId,
  });

  factory OnlineDevice.fromJson(Map<String, dynamic> json) {
    return OnlineDevice(
      brasid: json['brasid'] as String? ?? '',
      downFlow: json['downFlow'] as String? ?? '',
      hostName: json['hostName'] as String? ?? '',
      ip: json['ip'] as String? ?? '',
      loginTime: parseRfc9557ToShanghai(json['loginTime'] as String),
      mac: json['mac'] as String? ?? '',
      sessionId: json['sessionId'] as String? ?? '',
      terminalType: json['terminalType'] as String? ?? '',
      upFlow: json['upFlow'] as String? ?? '',
      useTime: (json['useTime'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
    );
  }

  final String brasid;
  final String downFlow;
  final String hostName;
  final String ip;
  final ShanghaiDateTime loginTime;
  final String mac;
  final String sessionId;
  final String terminalType;
  final String upFlow;
  final int useTime;
  final int userId;
}
