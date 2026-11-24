import 'dart:ffi';

import 'package:ffi/ffi.dart';

final class ZzuBuffer extends Opaque {}

final class ZzuCall extends Opaque {}

final class ZzuCasClient extends Opaque {}

final class ZzuEasClient extends Opaque {}

final class ZzuEcardClient extends Opaque {}

final class ZzuMailClient extends Opaque {}

final class ZzuPortalClient extends Opaque {}

final class ZzuRuntimeOpaque extends Opaque {}

final class ZzuSelfServiceClient extends Opaque {}

typedef ZzuCallbackNative = Void Function(
  Pointer<Void> userData,
  Int32 status,
  Pointer<ZzuBuffer> payload,
);

typedef AbiVersionNative = Uint32 Function();
typedef AbiVersionDart = int Function();

typedef VersionNative = Pointer<Utf8> Function();
typedef VersionDart = Pointer<Utf8> Function();

typedef BufferDataNative = Pointer<Uint8> Function(Pointer<ZzuBuffer> buffer);
typedef BufferDataDart = Pointer<Uint8> Function(Pointer<ZzuBuffer> buffer);

typedef BufferLenNative = Size Function(Pointer<ZzuBuffer> buffer);
typedef BufferLenDart = int Function(Pointer<ZzuBuffer> buffer);

typedef BufferFreeNative = Void Function(Pointer<ZzuBuffer> buffer);
typedef BufferFreeDart = void Function(Pointer<ZzuBuffer> buffer);

typedef CallCancelNative = Void Function(Pointer<ZzuCall> call);
typedef CallCancelDart = void Function(Pointer<ZzuCall> call);

typedef CallFreeNative = Void Function(Pointer<ZzuCall> call);
typedef CallFreeDart = void Function(Pointer<ZzuCall> call);

typedef RuntimeNewNative = Int32 Function(
  Pointer<Uint8> options,
  Size optionsLen,
  Pointer<Pointer<ZzuRuntimeOpaque>> outRuntime,
  Pointer<Pointer<ZzuBuffer>> outError,
);
typedef RuntimeNewDart = int Function(
  Pointer<Uint8> options,
  int optionsLen,
  Pointer<Pointer<ZzuRuntimeOpaque>> outRuntime,
  Pointer<Pointer<ZzuBuffer>> outError,
);

typedef ClientFromRuntimeNewNative<T extends NativeType> = Int32 Function(
  Pointer<ZzuRuntimeOpaque> runtime,
  Pointer<Uint8> config,
  Size configLen,
  Pointer<Pointer<T>> outClient,
  Pointer<Pointer<ZzuBuffer>> outError,
);
typedef ClientFromRuntimeNewDart<T extends NativeType> = int Function(
  Pointer<ZzuRuntimeOpaque> runtime,
  Pointer<Uint8> config,
  int configLen,
  Pointer<Pointer<T>> outClient,
  Pointer<Pointer<ZzuBuffer>> outError,
);

typedef ClientFromCasNewNative<T extends NativeType> = Int32 Function(
  Pointer<ZzuCasClient> cas,
  Pointer<Pointer<T>> outClient,
  Pointer<Pointer<ZzuBuffer>> outError,
);
typedef ClientFromCasNewDart<T extends NativeType> = int Function(
  Pointer<ZzuCasClient> cas,
  Pointer<Pointer<T>> outClient,
  Pointer<Pointer<ZzuBuffer>> outError,
);

typedef HandleFreeNative = Void Function(Pointer<Void> handle);
typedef HandleFreeDart = void Function(Pointer<Void> handle);

typedef ClientCallNative<T extends NativeType> = Int32 Function(
  Pointer<T> client,
  Pointer<Utf8> method,
  Pointer<Uint8> params,
  Size paramsLen,
  Pointer<NativeFunction<ZzuCallbackNative>> callback,
  Pointer<Void> userData,
  Pointer<Pointer<ZzuCall>> outCall,
);
typedef ClientCallDart<T extends NativeType> = int Function(
  Pointer<T> client,
  Pointer<Utf8> method,
  Pointer<Uint8> params,
  int paramsLen,
  Pointer<NativeFunction<ZzuCallbackNative>> callback,
  Pointer<Void> userData,
  Pointer<Pointer<ZzuCall>> outCall,
);

typedef ClientCallBlockingNative<T extends NativeType> = Int32 Function(
  Pointer<T> client,
  Pointer<Utf8> method,
  Pointer<Uint8> params,
  Size paramsLen,
  Pointer<Pointer<ZzuBuffer>> outPayload,
);
typedef ClientCallBlockingDart<T extends NativeType> = int Function(
  Pointer<T> client,
  Pointer<Utf8> method,
  Pointer<Uint8> params,
  int paramsLen,
  Pointer<Pointer<ZzuBuffer>> outPayload,
);

typedef PortalDiscoverNative = Int32 Function(
  Pointer<ZzuRuntimeOpaque> runtime,
  Pointer<Uint8> options,
  Size optionsLen,
  Pointer<NativeFunction<ZzuCallbackNative>> callback,
  Pointer<Void> userData,
  Pointer<Pointer<ZzuCall>> outCall,
);
typedef PortalDiscoverDart = int Function(
  Pointer<ZzuRuntimeOpaque> runtime,
  Pointer<Uint8> options,
  int optionsLen,
  Pointer<NativeFunction<ZzuCallbackNative>> callback,
  Pointer<Void> userData,
  Pointer<Pointer<ZzuCall>> outCall,
);

typedef LocalBytesFnNative = Int32 Function(
  Pointer<Uint8> input,
  Size inputLen,
  Pointer<Pointer<ZzuBuffer>> outPayload,
);
typedef LocalBytesFnDart = int Function(
  Pointer<Uint8> input,
  int inputLen,
  Pointer<Pointer<ZzuBuffer>> outPayload,
);

class LibzzuBindings {
  LibzzuBindings(this.library)
      : zzuAbiVersion = library
            .lookupFunction<AbiVersionNative, AbiVersionDart>('zzu_abi_version'),
        zzuVersion =
            library.lookupFunction<VersionNative, VersionDart>('zzu_version'),
        zzuBufferData = library.lookupFunction<BufferDataNative, BufferDataDart>(
          'zzu_buffer_data',
        ),
        zzuBufferLen = library.lookupFunction<BufferLenNative, BufferLenDart>(
          'zzu_buffer_len',
        ),
        zzuBufferFree = library.lookupFunction<BufferFreeNative, BufferFreeDart>(
          'zzu_buffer_free',
        ),
        zzuCallCancel = library.lookupFunction<CallCancelNative, CallCancelDart>(
          'zzu_call_cancel',
        ),
        zzuCallFree =
            library.lookupFunction<CallFreeNative, CallFreeDart>('zzu_call_free'),
        zzuRuntimeNew = library.lookupFunction<RuntimeNewNative, RuntimeNewDart>(
          'zzu_runtime_new',
        ),
        zzuRuntimeFreePtr =
            library.lookup<NativeFunction<HandleFreeNative>>('zzu_runtime_free'),
        zzuRuntimeFree =
            library.lookupFunction<HandleFreeNative, HandleFreeDart>(
          'zzu_runtime_free',
        ),
        zzuCasNew = library.lookupFunction<
            ClientFromRuntimeNewNative<ZzuCasClient>,
            ClientFromRuntimeNewDart<ZzuCasClient>>('zzu_cas_new'),
        zzuCasFreePtr =
            library.lookup<NativeFunction<HandleFreeNative>>('zzu_cas_free'),
        zzuCasFree = library.lookupFunction<HandleFreeNative, HandleFreeDart>(
          'zzu_cas_free',
        ),
        zzuCasCall = library.lookupFunction<ClientCallNative<ZzuCasClient>,
            ClientCallDart<ZzuCasClient>>('zzu_cas_call'),
        zzuCasCallBlocking = library.lookupFunction<
            ClientCallBlockingNative<ZzuCasClient>,
            ClientCallBlockingDart<ZzuCasClient>>('zzu_cas_call_blocking'),
        zzuEasNew = library.lookupFunction<
            ClientFromCasNewNative<ZzuEasClient>,
            ClientFromCasNewDart<ZzuEasClient>>('zzu_eas_new'),
        zzuEasFreePtr =
            library.lookup<NativeFunction<HandleFreeNative>>('zzu_eas_free'),
        zzuEasFree = library.lookupFunction<HandleFreeNative, HandleFreeDart>(
          'zzu_eas_free',
        ),
        zzuEasCall = library.lookupFunction<ClientCallNative<ZzuEasClient>,
            ClientCallDart<ZzuEasClient>>('zzu_eas_call'),
        zzuEasCallBlocking = library.lookupFunction<
            ClientCallBlockingNative<ZzuEasClient>,
            ClientCallBlockingDart<ZzuEasClient>>('zzu_eas_call_blocking'),
        zzuEcardNew = library.lookupFunction<
            ClientFromCasNewNative<ZzuEcardClient>,
            ClientFromCasNewDart<ZzuEcardClient>>('zzu_ecard_new'),
        zzuEcardFreePtr =
            library.lookup<NativeFunction<HandleFreeNative>>('zzu_ecard_free'),
        zzuEcardFree = library.lookupFunction<HandleFreeNative, HandleFreeDart>(
          'zzu_ecard_free',
        ),
        zzuEcardCall = library.lookupFunction<ClientCallNative<ZzuEcardClient>,
            ClientCallDart<ZzuEcardClient>>('zzu_ecard_call'),
        zzuEcardCallBlocking = library.lookupFunction<
            ClientCallBlockingNative<ZzuEcardClient>,
            ClientCallBlockingDart<ZzuEcardClient>>('zzu_ecard_call_blocking'),
        zzuMailNew = library.lookupFunction<
            ClientFromCasNewNative<ZzuMailClient>,
            ClientFromCasNewDart<ZzuMailClient>>('zzu_mail_new'),
        zzuMailFreePtr =
            library.lookup<NativeFunction<HandleFreeNative>>('zzu_mail_free'),
        zzuMailFree = library.lookupFunction<HandleFreeNative, HandleFreeDart>(
          'zzu_mail_free',
        ),
        zzuMailCall = library.lookupFunction<ClientCallNative<ZzuMailClient>,
            ClientCallDart<ZzuMailClient>>('zzu_mail_call'),
        zzuMailCallBlocking = library.lookupFunction<
            ClientCallBlockingNative<ZzuMailClient>,
            ClientCallBlockingDart<ZzuMailClient>>('zzu_mail_call_blocking'),
        zzuPortalDiscover =
            library.lookupFunction<PortalDiscoverNative, PortalDiscoverDart>(
          'zzu_portal_discover',
        ),
        zzuPortalDiscoverBlocking =
            library.lookupFunction<LocalBytesFnNative, LocalBytesFnDart>(
          'zzu_portal_discover_blocking',
        ),
        zzuPortalNew = library.lookupFunction<
            ClientFromRuntimeNewNative<ZzuPortalClient>,
            ClientFromRuntimeNewDart<ZzuPortalClient>>('zzu_portal_new'),
        zzuPortalFreePtr =
            library.lookup<NativeFunction<HandleFreeNative>>('zzu_portal_free'),
        zzuPortalFree =
            library.lookupFunction<HandleFreeNative, HandleFreeDart>(
          'zzu_portal_free',
        ),
        zzuPortalCall = library.lookupFunction<ClientCallNative<ZzuPortalClient>,
            ClientCallDart<ZzuPortalClient>>('zzu_portal_call'),
        zzuPortalCallBlocking = library.lookupFunction<
            ClientCallBlockingNative<ZzuPortalClient>,
            ClientCallBlockingDart<ZzuPortalClient>>('zzu_portal_call_blocking'),
        zzuSelfServiceNew = library.lookupFunction<
            ClientFromRuntimeNewNative<ZzuSelfServiceClient>,
            ClientFromRuntimeNewDart<ZzuSelfServiceClient>>(
          'zzu_self_service_new',
        ),
        zzuSelfServiceFreePtr = library.lookup<NativeFunction<HandleFreeNative>>(
          'zzu_self_service_free',
        ),
        zzuSelfServiceFree =
            library.lookupFunction<HandleFreeNative, HandleFreeDart>(
          'zzu_self_service_free',
        ),
        zzuSelfServiceCall = library.lookupFunction<
            ClientCallNative<ZzuSelfServiceClient>,
            ClientCallDart<ZzuSelfServiceClient>>('zzu_self_service_call'),
        zzuSelfServiceCallBlocking = library.lookupFunction<
            ClientCallBlockingNative<ZzuSelfServiceClient>,
            ClientCallBlockingDart<ZzuSelfServiceClient>>(
          'zzu_self_service_call_blocking',
        ),
        zzuTimetableToCalendar =
            library.lookupFunction<LocalBytesFnNative, LocalBytesFnDart>(
          'zzu_timetable_to_calendar',
        ),
        zzuRoomIdParse =
            library.lookupFunction<LocalBytesFnNative, LocalBytesFnDart>(
          'zzu_room_id_parse',
        ),
        zzuRechargeOrderCheckoutUrlFor =
            library.lookupFunction<LocalBytesFnNative, LocalBytesFnDart>(
          'zzu_recharge_order_checkout_url_for',
        );

  final DynamicLibrary library;

  final AbiVersionDart zzuAbiVersion;
  final VersionDart zzuVersion;
  final BufferDataDart zzuBufferData;
  final BufferLenDart zzuBufferLen;
  final BufferFreeDart zzuBufferFree;
  final CallCancelDart zzuCallCancel;
  final CallFreeDart zzuCallFree;

  final RuntimeNewDart zzuRuntimeNew;
  final Pointer<NativeFunction<HandleFreeNative>> zzuRuntimeFreePtr;
  final HandleFreeDart zzuRuntimeFree;

  final ClientFromRuntimeNewDart<ZzuCasClient> zzuCasNew;
  final Pointer<NativeFunction<HandleFreeNative>> zzuCasFreePtr;
  final HandleFreeDart zzuCasFree;
  final ClientCallDart<ZzuCasClient> zzuCasCall;
  final ClientCallBlockingDart<ZzuCasClient> zzuCasCallBlocking;

  final ClientFromCasNewDart<ZzuEasClient> zzuEasNew;
  final Pointer<NativeFunction<HandleFreeNative>> zzuEasFreePtr;
  final HandleFreeDart zzuEasFree;
  final ClientCallDart<ZzuEasClient> zzuEasCall;
  final ClientCallBlockingDart<ZzuEasClient> zzuEasCallBlocking;

  final ClientFromCasNewDart<ZzuEcardClient> zzuEcardNew;
  final Pointer<NativeFunction<HandleFreeNative>> zzuEcardFreePtr;
  final HandleFreeDart zzuEcardFree;
  final ClientCallDart<ZzuEcardClient> zzuEcardCall;
  final ClientCallBlockingDart<ZzuEcardClient> zzuEcardCallBlocking;

  final ClientFromCasNewDart<ZzuMailClient> zzuMailNew;
  final Pointer<NativeFunction<HandleFreeNative>> zzuMailFreePtr;
  final HandleFreeDart zzuMailFree;
  final ClientCallDart<ZzuMailClient> zzuMailCall;
  final ClientCallBlockingDart<ZzuMailClient> zzuMailCallBlocking;

  final PortalDiscoverDart zzuPortalDiscover;
  final LocalBytesFnDart zzuPortalDiscoverBlocking;

  final ClientFromRuntimeNewDart<ZzuPortalClient> zzuPortalNew;
  final Pointer<NativeFunction<HandleFreeNative>> zzuPortalFreePtr;
  final HandleFreeDart zzuPortalFree;
  final ClientCallDart<ZzuPortalClient> zzuPortalCall;
  final ClientCallBlockingDart<ZzuPortalClient> zzuPortalCallBlocking;

  final ClientFromRuntimeNewDart<ZzuSelfServiceClient> zzuSelfServiceNew;
  final Pointer<NativeFunction<HandleFreeNative>> zzuSelfServiceFreePtr;
  final HandleFreeDart zzuSelfServiceFree;
  final ClientCallDart<ZzuSelfServiceClient> zzuSelfServiceCall;
  final ClientCallBlockingDart<ZzuSelfServiceClient> zzuSelfServiceCallBlocking;

  final LocalBytesFnDart zzuTimetableToCalendar;
  final LocalBytesFnDart zzuRoomIdParse;
  final LocalBytesFnDart zzuRechargeOrderCheckoutUrlFor;
}
