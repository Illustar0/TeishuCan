class MeterOption {
  const MeterOption({required this.id, required this.name});

  final String id;
  final String name;

  @override
  bool operator ==(Object other) =>
      other is MeterOption && id == other.id && name == other.name;

  @override
  int get hashCode => Object.hash(id, name);
}

class MeterLocation {
  const MeterLocation({required this.meterId, required this.path});

  factory MeterLocation.fromJson(Map<String, dynamic> json) => MeterLocation(
        meterId: json['meterId'] as String,
        path: json['path'] as String,
      );

  final String meterId;
  final String path;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'meterId': meterId,
        'path': path,
      };
}

class ElectricityMeter {
  const ElectricityMeter({
    required this.id,
    required this.name,
    required this.location,
  });

  factory ElectricityMeter.fromJson(Map<String, dynamic> json) =>
      ElectricityMeter(
        id: json['id'] as String,
        name: json['name'] as String,
        location:
            MeterLocation.fromJson(json['location'] as Map<String, dynamic>),
      );

  final String id;
  final String name;
  final MeterLocation location;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'name': name,
        'location': location.toJson(),
      };
}

class MeterReading {
  const MeterReading({required this.meter, required this.kilowattHours});

  final ElectricityMeter meter;
  final double? kilowattHours;
}

double? totalKilowattHours(List<MeterReading> readings) {
  if (readings.isEmpty) return null;
  var sum = 0.0;
  for (final reading in readings) {
    final value = reading.kilowattHours;
    if (value == null) return null;
    sum += value;
  }
  return sum;
}

/// Builds the `locations(parent)` key (`area`, `area-building`, `area-building--level`).
String locationParent(List<MeterOption> path) {
  final building = path.take(2).map((o) => o.id).join('-');
  if (path.length >= 3) {
    return '$building--${path[2].id}';
  }
  return building;
}

sealed class MeterRechargeResult {
  const MeterRechargeResult();
}

final class MeterRechargeSucceeded extends MeterRechargeResult {
  const MeterRechargeSucceeded();
}

final class MeterRechargeRejected extends MeterRechargeResult {
  const MeterRechargeRejected(this.reason);
  final String? reason;
}

final class MeterRechargeUnconfirmed extends MeterRechargeResult {
  const MeterRechargeUnconfirmed();
}

class PaymentChannelInfo {
  const PaymentChannelInfo({
    required this.name,
    required this.checkoutUrl,
  });

  final String name;
  final String checkoutUrl;
}

class CampusCardRechargeOrder {
  const CampusCardRechargeOrder({
    required this.amountYuan,
    required this.channels,
  });

  final int amountYuan;
  final List<PaymentChannelInfo> channels;
}
