#include <stddef.h>
#include <stdint.h>

#include "libzzu_force_link.h"

extern void zzu_abi_version(void);
extern void zzu_version(void);
extern void zzu_buffer_data(void);
extern void zzu_buffer_len(void);
extern void zzu_buffer_free(void);
extern void zzu_call_cancel(void);
extern void zzu_call_free(void);
extern void zzu_runtime_new(void);
extern void zzu_runtime_free(void);
extern void zzu_cas_new(void);
extern void zzu_cas_free(void);
extern void zzu_cas_call(void);
extern void zzu_cas_call_blocking(void);
extern void zzu_eas_new(void);
extern void zzu_eas_free(void);
extern void zzu_eas_call(void);
extern void zzu_eas_call_blocking(void);
extern void zzu_ecard_new(void);
extern void zzu_ecard_free(void);
extern void zzu_ecard_call(void);
extern void zzu_ecard_call_blocking(void);
extern void zzu_mail_new(void);
extern void zzu_mail_free(void);
extern void zzu_mail_call(void);
extern void zzu_mail_call_blocking(void);
extern void zzu_portal_discover(void);
extern void zzu_portal_discover_blocking(void);
extern void zzu_portal_new(void);
extern void zzu_portal_free(void);
extern void zzu_portal_call(void);
extern void zzu_portal_call_blocking(void);
extern void zzu_self_service_new(void);
extern void zzu_self_service_free(void);
extern void zzu_self_service_call(void);
extern void zzu_self_service_call_blocking(void);
extern void zzu_timetable_to_calendar(void);
extern void zzu_room_id_parse(void);
extern void zzu_recharge_order_checkout_url_for(void);

__attribute__((used))
const void *const kLibzzuForceLinkTable[] = {
    (const void *)&zzu_abi_version,
    (const void *)&zzu_version,
    (const void *)&zzu_buffer_data,
    (const void *)&zzu_buffer_len,
    (const void *)&zzu_buffer_free,
    (const void *)&zzu_call_cancel,
    (const void *)&zzu_call_free,
    (const void *)&zzu_runtime_new,
    (const void *)&zzu_runtime_free,
    (const void *)&zzu_cas_new,
    (const void *)&zzu_cas_free,
    (const void *)&zzu_cas_call,
    (const void *)&zzu_cas_call_blocking,
    (const void *)&zzu_eas_new,
    (const void *)&zzu_eas_free,
    (const void *)&zzu_eas_call,
    (const void *)&zzu_eas_call_blocking,
    (const void *)&zzu_ecard_new,
    (const void *)&zzu_ecard_free,
    (const void *)&zzu_ecard_call,
    (const void *)&zzu_ecard_call_blocking,
    (const void *)&zzu_mail_new,
    (const void *)&zzu_mail_free,
    (const void *)&zzu_mail_call,
    (const void *)&zzu_mail_call_blocking,
    (const void *)&zzu_portal_discover,
    (const void *)&zzu_portal_discover_blocking,
    (const void *)&zzu_portal_new,
    (const void *)&zzu_portal_free,
    (const void *)&zzu_portal_call,
    (const void *)&zzu_portal_call_blocking,
    (const void *)&zzu_self_service_new,
    (const void *)&zzu_self_service_free,
    (const void *)&zzu_self_service_call,
    (const void *)&zzu_self_service_call_blocking,
    (const void *)&zzu_timetable_to_calendar,
    (const void *)&zzu_room_id_parse,
    (const void *)&zzu_recharge_order_checkout_url_for,
};

const void *libzzu_force_link(void) {
  // Touch all entries via volatile accumulator so Release -Os cannot fold away slots 1..37.
  volatile uintptr_t acc = 0;
  const size_t count =
      sizeof(kLibzzuForceLinkTable) / sizeof(kLibzzuForceLinkTable[0]);
  for (size_t i = 0; i < count; i++) {
    acc ^= (uintptr_t)kLibzzuForceLinkTable[i];
  }
  return acc != 0 ? kLibzzuForceLinkTable[0] : NULL;
}
