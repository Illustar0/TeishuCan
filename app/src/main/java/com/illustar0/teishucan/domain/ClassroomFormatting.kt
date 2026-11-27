package com.illustar0.teishucan.domain

import kotlinx.datetime.LocalTime

private const val MinSnowflakeCodeLength = 10

private val RedundantActivityPrefixes = listOf(
    "实验项目：",
    "实验项目:",
    "活动名称：",
    "活动名称:",
    "课程：",
    "课程:",
    "实验：",
    "实验:",
    "借用：",
    "借用:",
    "考试：",
    "考试:",
    "调课：",
    "调课:",
)

/**
 * 从教务排课的原始活动名里拆出的展示信息：
 * 普通课程拆出课程名 [title]、开课院系 [department] 和教学班号 [code]；
 * 形如 `52011025(人体成分测定与分析)` 的实验课提取括号里的实验名作为 [title]，并标上 [isExperimentCode]。
 */
data class ParsedOccupationActivity(
    val title: String,
    val department: String = "",
    val code: String = "",
    val isExperimentCode: Boolean = false,
)

data class OccupationTimeSlot(
    val startTime: LocalTime,
    val endTime: LocalTime,
)

data class MergedClassroomOccupation(
    val activityKind: ClassroomActivityKind,
    val activity: ParsedOccupationActivity,
    val teachers: List<String>,
    val slots: List<OccupationTimeSlot>,
)

/** 筛掉空白、与 [roomName] 重复的文本和 10 位以上的纯数字后台 ID，只留下真正有用的门牌号。 */
fun meaningfulRoomCode(code: String, roomName: String): String {
    val trimmed = code.trim()
    val isMeaningless = trimmed.isEmpty() ||
        trimmed == roomName.trim() ||
        (trimmed.length >= MinSnowflakeCodeLength && trimmed.all(Char::isAsciiDigit))
    return if (isMeaningless) "" else trimmed
}

fun parseOccupationActivity(rawName: String): ParsedOccupationActivity {
    val stripped = stripRedundantActivityPrefix(rawName.trim())
    val openParenIndex = if (stripped.endsWith(')')) stripped.lastIndexOf('(') else -1
    if (openParenIndex <= 0) return ParsedOccupationActivity(title = stripped)

    val prefix = stripped.substring(0, openParenIndex).trim()
    val inner = stripped.substring(openParenIndex + 1, stripped.lastIndex).trim()
    val hasValidParts = prefix.isNotEmpty() && inner.isNotEmpty() && !inner.contains(')')
    val commaIndex = inner.indexOfFirst { it == ',' || it == '，' }

    return when {
        !hasValidParts -> ParsedOccupationActivity(title = stripped)
        isAsciiActivityCode(prefix) && commaIndex < 0 -> ParsedOccupationActivity(
            title = inner,
            code = prefix,
            isExperimentCode = true,
        )
        commaIndex > 0 -> {
            val courseCode = inner.substring(0, commaIndex).trim()
            val department = inner.substring(commaIndex + 1).trim()
            if (isAsciiActivityCode(courseCode) && department.isNotEmpty()) {
                ParsedOccupationActivity(title = prefix, department = department, code = courseCode)
            } else {
                ParsedOccupationActivity(title = stripped)
            }
        }
        else -> ParsedOccupationActivity(title = stripped)
    }
}

/** 同一间教室当天同类别、同名、同老师的排课合成一条，合并多段上课时间，并把逗号分隔的老师拆成列表。 */
fun mergeClassroomOccupations(occupations: List<ClassroomOccupation>): List<MergedClassroomOccupation> {
    if (occupations.isEmpty()) return emptyList()
    data class MergeKey(
        val kind: ClassroomActivityKind,
        val rawName: String,
        val rawTeacher: String,
    )
    val grouped = LinkedHashMap<MergeKey, MutableList<OccupationTimeSlot>>()
    for (occupation in occupations) {
        val key = MergeKey(
            kind = occupation.activityKind,
            rawName = occupation.activityName.trim(),
            rawTeacher = occupation.teacherName.trim(),
        )
        grouped.getOrPut(key) { mutableListOf() }.add(
            OccupationTimeSlot(startTime = occupation.startTime, endTime = occupation.endTime),
        )
    }
    return grouped.map { (key, slots) ->
        MergedClassroomOccupation(
            activityKind = key.kind,
            activity = parseOccupationActivity(key.rawName),
            teachers = splitTeacherNames(key.rawTeacher),
            slots = slots,
        )
    }
}

private fun stripRedundantActivityPrefix(text: String): String {
    val matchedPrefix = RedundantActivityPrefixes.firstOrNull { prefix -> text.startsWith(prefix) }
        ?: return text
    return text.substring(matchedPrefix.length).trimStart()
}

private fun isAsciiActivityCode(candidate: String): Boolean =
    candidate.isNotEmpty() &&
        candidate.any(Char::isAsciiDigit) &&
        candidate.all { ch -> ch.isAsciiDigit() || ch in 'A'..'Z' || ch in 'a'..'z' || ch == '-' || ch == '_' }

private fun splitTeacherNames(rawTeacher: String): List<String> {
    if (rawTeacher.isBlank()) return emptyList()
    return rawTeacher
        .split(',', '，')
        .map(String::trim)
        .filter(String::isNotEmpty)
}
