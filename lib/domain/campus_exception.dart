enum CampusFeature {
  account,
  profile,
  timetable,
  campusCard,
  electricity,
  services,
}

class CampusRequestException implements Exception {
  const CampusRequestException(
    this.feature, {
    this.reason,
    this.cause,
  });

  final CampusFeature feature;
  final String? reason;
  final Object? cause;

  @override
  String toString() =>
      'CampusRequestException($feature, reason: $reason, cause: $cause)';
}

class InvalidMeterLocationException implements Exception {
  const InvalidMeterLocationException();

  @override
  String toString() => 'Select the meter location again';
}
