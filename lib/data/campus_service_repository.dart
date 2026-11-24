import 'package:libzzu_ffi/libzzu_ffi.dart';

import '../domain/campus_exception.dart';
import '../domain/campus_service.dart';
import 'zzu_session.dart';

class CampusServiceRepository {
  CampusServiceRepository(this._session);

  final ZzuSessionController _session;

  Future<String> loginUrl(CampusService service) =>
      _session.request(CampusFeature.services, (clients, _) {
        return clients.cas.serviceSsoUrl(_toSsoService(service));
      });
}

SsoService _toSsoService(CampusService service) => switch (service) {
      CampusService.library => SsoService.library,
      CampusService.libraryReservation => SsoService.libraryReservation,
      CampusService.campusCard => SsoService.ecard,
      CampusService.mail => SsoService.email,
      CampusService.leave => SsoService.leave,
      CampusService.presidentMailbox => SsoService.presidentMailbox,
      CampusService.studentStatus => SsoService.undergradStudentStatus,
      CampusService.programCompletion => SsoService.undergradProgramCompletion,
      CampusService.academicWarnings => SsoService.undergradAcademicWarnings,
      CampusService.exams => SsoService.undergradExams,
      CampusService.grades => SsoService.undergradGrades,
      CampusService.timetable => SsoService.undergradTimetable,
      CampusService.courseOfferings => SsoService.undergradCourseOfferings,
      CampusService.schoolCalendar => SsoService.undergradSchoolCalendar,
      CampusService.availableClassrooms =>
        SsoService.undergradAvailableClassrooms,
      CampusService.classroomTimetable =>
        SsoService.undergradClassroomTimetable,
    };
