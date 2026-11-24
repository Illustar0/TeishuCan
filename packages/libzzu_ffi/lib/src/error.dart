import 'dart:convert';

abstract final class ZzuStatus {
  static const int ok = 0;
  static const int invalidInput = 1;
  static const int transportError = 2;
  static const int httpError = 3;
  static const int decodeError = 4;
  static const int authenticationError = 5;
  static const int remoteError = 6;
  static const int notFound = 7;
  static const int cryptoError = 8;
  static const int runtimeError = 9;
  static const int panic = 10;
  static const int unknownError = 11;
  static const int timeout = 12;
  static const int cancelled = 13;
  static const int unknownMethod = 14;
}

abstract final class AuthErrorKind {
  static const String invalidCredentials = 'invalid_credentials';
  static const String notAuthenticated = 'not_authenticated';
  static const String tokenExpired = 'token_expired';
  static const String mfaRequired = 'mfa_required';
  static const String invalidMfaState = 'invalid_mfa_state';
  static const String mfaVerificationFailed = 'mfa_verification_failed';
}

abstract final class TransportErrorKind {
  static const String timeout = 'timeout';
  static const String connection = 'connection';
}

class ZzuRequestContext {
  const ZzuRequestContext({
    required this.method,
    required this.url,
    this.statusCode,
    this.errorCode,
    this.responseBody,
  });

  factory ZzuRequestContext.fromJson(Map<String, dynamic> json) {
    return ZzuRequestContext(
      method: json['method'] as String? ?? '',
      url: json['url'] as String? ?? '',
      statusCode: (json['statusCode'] as num?)?.toInt(),
      errorCode: json['errorCode'] as String?,
      responseBody: json['responseBody'] as String?,
    );
  }

  final String method;
  final String url;
  final int? statusCode;
  final String? errorCode;
  final String? responseBody;
}

sealed class ZzuException implements Exception {
  const ZzuException({
    required this.status,
    required this.message,
    this.kind,
    this.cause,
    this.request,
  });

  final int status;
  final String message;
  final String? kind;
  final String? cause;
  final ZzuRequestContext? request;

  @override
  String toString() {
    final kindPart = kind == null ? '' : ' [$kind]';
    final causePart = cause == null ? '' : ': $cause';
    return '$runtimeType($status$kindPart): $message$causePart';
  }

  static ZzuException fromPayload(int status, String rawPayload) {
    var parsedStatus = status;
    String? kind;
    var message = 'libzzu error ($status)';
    String? cause;
    ZzuRequestContext? request;

    if (rawPayload.isNotEmpty) {
      try {
        final decoded = jsonDecode(rawPayload);
        if (decoded is Map<String, dynamic>) {
          parsedStatus = (decoded['status'] as num?)?.toInt() ?? status;
          kind = decoded['kind'] as String?;
          message = decoded['message'] as String? ?? message;
          cause = decoded['cause'] as String?;
          final req = decoded['request'];
          if (req is Map<String, dynamic>) {
            request = ZzuRequestContext.fromJson(req);
          }
        }
      } catch (_) {
        message = rawPayload;
      }
    }

    return switch (parsedStatus) {
      ZzuStatus.invalidInput => ZzuInvalidInputException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      ZzuStatus.transportError => ZzuTransportException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      ZzuStatus.httpError => ZzuHttpException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      ZzuStatus.decodeError => ZzuDecodeException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      ZzuStatus.authenticationError => ZzuAuthenticationException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      ZzuStatus.remoteError => ZzuRemoteException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      ZzuStatus.notFound => ZzuNotFoundException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      ZzuStatus.cryptoError => ZzuCryptoException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      ZzuStatus.runtimeError => ZzuRuntimeException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      ZzuStatus.panic => ZzuPanicException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      ZzuStatus.timeout => ZzuTimeoutException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      ZzuStatus.cancelled => ZzuCancelledException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      ZzuStatus.unknownMethod => ZzuUnknownMethodException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
      _ => ZzuUnknownException(
          status: parsedStatus,
          message: message,
          kind: kind,
          cause: cause,
          request: request,
        ),
    };
  }
}

final class ZzuInvalidInputException extends ZzuException {
  const ZzuInvalidInputException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuTransportException extends ZzuException {
  const ZzuTransportException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuHttpException extends ZzuException {
  const ZzuHttpException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuDecodeException extends ZzuException {
  const ZzuDecodeException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuAuthenticationException extends ZzuException {
  const ZzuAuthenticationException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuRemoteException extends ZzuException {
  const ZzuRemoteException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuNotFoundException extends ZzuException {
  const ZzuNotFoundException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuCryptoException extends ZzuException {
  const ZzuCryptoException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuRuntimeException extends ZzuException {
  const ZzuRuntimeException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuPanicException extends ZzuException {
  const ZzuPanicException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuTimeoutException extends ZzuException {
  const ZzuTimeoutException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuCancelledException extends ZzuException {
  const ZzuCancelledException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuUnknownMethodException extends ZzuException {
  const ZzuUnknownMethodException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}

final class ZzuUnknownException extends ZzuException {
  const ZzuUnknownException({
    required super.status,
    required super.message,
    super.kind,
    super.cause,
    super.request,
  });
}
