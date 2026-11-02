package com.illustar0.chibacan.domain

import kotlinx.serialization.Serializable

/** 电表层级中某一级的一个选项；最后一级的 [id] 就是电表本身。 */
data class MeterOption(val id: String, val name: String)

/** 电表所在位置：[meterId] 用于查询读数，[path] 是展示给用户的路径。 */
@Serializable
data class MeterLocation(val meterId: String, val path: String)

/** 用户以 [name] 命名添加的电表。同一位置可以添加多次，因此用 [id] 区分。 */
@Serializable
data class ElectricityMeter(val id: String, val name: String, val location: MeterLocation)

/** [meter] 的剩余电量，尚未查询到时 [kilowattHours] 为 null。 */
data class MeterReading(val meter: ElectricityMeter, val kilowattHours: Double?)

/** 所有电表的剩余电量之和；有任意一块尚未查询到时为 null。 */
fun List<MeterReading>.totalKilowattHours(): Double? =
    takeIf { readings -> readings.isNotEmpty() && readings.all { it.kilowattHours != null } }
        ?.sumOf { checkNotNull(it.kilowattHours) }

class InvalidMeterLocationException : Exception("Select the meter location again")

sealed interface MeterRechargeResult {
    data object Succeeded : MeterRechargeResult

    /** 确定没有扣款。[reason] 是校园服务返回的原话，可能没有。 */
    data class Rejected(val reason: String?) : MeterRechargeResult

    /** 可能已经扣款，重试前必须先核对余额。 */
    data object Unconfirmed : MeterRechargeResult
}
