package com.illustar0.teishucan.data

import com.illustar0.zzu.ZzuTimeZone
import java.time.ZoneId
import kotlinx.datetime.toJavaZoneId

/** 校园服务给出的日期与时刻所在的时区，与设备当前所在地无关。 */
val CampusZone: ZoneId = ZzuTimeZone.toJavaZoneId()
