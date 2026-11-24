class StudentProfile {
  const StudentProfile({
    required this.name,
    required this.faculty,
    required this.identityType,
  });

  factory StudentProfile.fromJson(Map<String, dynamic> json) => StudentProfile(
        name: json['name'] as String? ?? '',
        faculty: json['faculty'] as String? ?? '',
        identityType: json['identityType'] as String? ?? '',
      );

  final String name;
  final String faculty;
  final String identityType;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'name': name,
        'faculty': faculty,
        'identityType': identityType,
      };
}

sealed class AccountState {
  const AccountState();
}

final class AccountRestoring extends AccountState {
  const AccountRestoring();
}

final class AccountSignedOut extends AccountState {
  const AccountSignedOut();
}

final class AccountSignedIn extends AccountState {
  const AccountSignedIn(this.profile);
  final StudentProfile profile;
}

sealed class LoginResult {
  const LoginResult();
}

final class LoginSignedIn extends LoginResult {
  const LoginSignedIn();
}

final class LoginMfaRequired extends LoginResult {
  const LoginMfaRequired({this.securePhone});
  final String? securePhone;
}
