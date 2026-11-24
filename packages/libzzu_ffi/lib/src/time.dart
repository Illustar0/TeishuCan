class ShanghaiDate implements Comparable<ShanghaiDate> {
  const ShanghaiDate(this.year, this.month, this.day);

  final int year;
  final int month;
  final int day;

  DateTime toUtcMidnight() => DateTime.utc(year, month, day);

  /// ISO weekday: 1 (Monday) .. 7 (Sunday).
  int get weekday => toUtcMidnight().weekday;

  ShanghaiDate addDays(int days) {
    final dt = DateTime.utc(year, month, day + days);
    return ShanghaiDate(dt.year, dt.month, dt.day);
  }

  String toIsoString() =>
      '${year.toString().padLeft(4, '0')}-'
      '${month.toString().padLeft(2, '0')}-'
      '${day.toString().padLeft(2, '0')}';

  @override
  int compareTo(ShanghaiDate other) {
    final y = year.compareTo(other.year);
    if (y != 0) return y;
    final m = month.compareTo(other.month);
    if (m != 0) return m;
    return day.compareTo(other.day);
  }

  @override
  bool operator ==(Object other) =>
      other is ShanghaiDate &&
      year == other.year &&
      month == other.month &&
      day == other.day;

  @override
  int get hashCode => Object.hash(year, month, day);

  @override
  String toString() => toIsoString();
}

/// Wall-clock date and time in Asia/Shanghai (UTC+8), independent of the host device timezone.
class ShanghaiDateTime implements Comparable<ShanghaiDateTime> {
  const ShanghaiDateTime({
    required this.year,
    required this.month,
    required this.day,
    required this.hour,
    required this.minute,
    this.second = 0,
  });

  factory ShanghaiDateTime.fromUtcInstant(DateTime instant) {
    final shanghai = instant.toUtc().add(const Duration(hours: 8));
    return ShanghaiDateTime(
      year: shanghai.year,
      month: shanghai.month,
      day: shanghai.day,
      hour: shanghai.hour,
      minute: shanghai.minute,
      second: shanghai.second,
    );
  }

  factory ShanghaiDateTime.now() =>
      ShanghaiDateTime.fromUtcInstant(DateTime.now().toUtc());

  final int year;
  final int month;
  final int day;
  final int hour;
  final int minute;
  final int second;

  ShanghaiDate get date => ShanghaiDate(year, month, day);

  DateTime toUtcInstant() =>
      DateTime.utc(year, month, day, hour, minute, second)
          .subtract(const Duration(hours: 8));

  @override
  int compareTo(ShanghaiDateTime other) =>
      toUtcInstant().compareTo(other.toUtcInstant());

  @override
  bool operator ==(Object other) =>
      other is ShanghaiDateTime &&
      year == other.year &&
      month == other.month &&
      day == other.day &&
      hour == other.hour &&
      minute == other.minute &&
      second == other.second;

  @override
  int get hashCode => Object.hash(year, month, day, hour, minute, second);

  @override
  String toString() =>
      '${date.toIsoString()}T'
      '${hour.toString().padLeft(2, '0')}:'
      '${minute.toString().padLeft(2, '0')}:'
      '${second.toString().padLeft(2, '0')}+08:00[Asia/Shanghai]';
}

ShanghaiDate parseLocalDate(String value) {
  final parts = value.split('-');
  if (parts.length != 3) {
    throw FormatException('Invalid local date: $value');
  }
  return ShanghaiDate(
    int.parse(parts[0]),
    int.parse(parts[1]),
    int.parse(parts[2]),
  );
}

ShanghaiDateTime parseRfc9557ToShanghai(String value) {
  final bracketIndex = value.indexOf('[');
  final rfc3339 = bracketIndex >= 0 ? value.substring(0, bracketIndex) : value;
  final instant = DateTime.parse(rfc3339);
  return ShanghaiDateTime.fromUtcInstant(instant);
}
