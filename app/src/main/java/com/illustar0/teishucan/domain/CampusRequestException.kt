package com.illustar0.teishucan.domain

enum class CampusFeature { Account, Timetable, CampusCard, Electricity, Mail, Services }

/** 校园服务请求失败；[feature] 决定失败的场景，[reason] 是校园服务给出的原话，没有时为 null。 */
class CampusRequestException(val feature: CampusFeature, val reason: String?, cause: Exception) :
    Exception("Campus request failed: $feature", cause)

/** 校园服务给出的失败原因，其他异常没有。 */
val Exception.campusReason: String?
    get() = (this as? CampusRequestException)?.reason
