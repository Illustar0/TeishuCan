package com.illustar0.teishucan.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ConflictChoiceTest {
    private val wednesday = LocalDate(2026, 11, 25)

    private fun entry(classId: Long, start: LocalTime = LocalTime(10, 10)) = TimetableEntry(
        classId = classId,
        day = DayOfWeek.WEDNESDAY,
        date = wednesday,
        startPeriod = 3,
        startTime = start,
        endTime = LocalTime(11, 50),
        title = "Course $classId",
        teacher = "",
        periodSpan = 2,
    )

    private val dataStructures = entry(classId = 1)
    private val networks = entry(classId = 2)
    private val elective = entry(classId = 3)

    @Test
    fun frontIn_withoutMatchingChoice_isNull() {
        val otherConflict = ConflictChoice(classIds = setOf(7, 8), frontClassId = 7)

        assertNull(listOf(otherConflict).frontIn(listOf(dataStructures, networks)))
    }

    @Test
    fun frontIn_choiceForSameClasses_returnsChosenEntry() {
        val choice = ConflictChoice(classIds = setOf(1, 2), frontClassId = 2)

        assertSame(networks, listOf(choice).frontIn(listOf(dataStructures, networks)))
    }

    @Test
    fun frontIn_choiceFromLargerConflictWithFrontPresent_applies() {
        val choice = ConflictChoice(classIds = setOf(1, 2, 3), frontClassId = 1)

        assertSame(dataStructures, listOf(choice).frontIn(listOf(dataStructures, networks)))
    }

    @Test
    fun frontIn_choiceFromLargerConflictWithFrontMissing_doesNotApply() {
        val choice = ConflictChoice(classIds = setOf(1, 2, 3), frontClassId = 3)

        assertNull(listOf(choice).frontIn(listOf(dataStructures, networks)))
    }

    @Test
    fun frontIn_severalApplicableChoices_latestWins() {
        val earlier = ConflictChoice(classIds = setOf(1, 2), frontClassId = 1)
        val later = ConflictChoice(classIds = setOf(1, 2, 3), frontClassId = 2)

        assertSame(networks, listOf(earlier, later).frontIn(listOf(dataStructures, networks)))
    }

    @Test
    fun withFront_replacesChoiceForSameClassesAndAppendsIt() {
        val sameClasses = ConflictChoice(classIds = setOf(1, 2), frontClassId = 1)
        val unrelated = ConflictChoice(classIds = setOf(3, 4), frontClassId = 4)

        val choices = listOf(sameClasses, unrelated).withFront(listOf(dataStructures, networks), networks)

        assertEquals(listOf(unrelated, ConflictChoice(classIds = setOf(1, 2), frontClassId = 2)), choices)
    }

    @Test(expected = IllegalArgumentException::class)
    fun withFront_frontOutsideConflict_isRejected() {
        emptyList<ConflictChoice>().withFront(listOf(dataStructures, networks), elective)
    }

    @Test
    fun upcomingClassAfter_simultaneousClassesWithChoice_picksChosenClass() {
        val term = termWith(dataStructures, networks)
        val choices = listOf(ConflictChoice(classIds = setOf(1, 2), frontClassId = 2))

        val upcoming = listOf(term).upcomingClassAfter(morningOf(wednesday), choices)

        assertEquals(networks.title, (upcoming as NextClass).name)
    }

    @Test
    fun upcomingClassAfter_simultaneousClassesWithoutChoice_keepsDataOrder() {
        val term = termWith(dataStructures, networks)

        val upcoming = listOf(term).upcomingClassAfter(morningOf(wednesday), emptyList())

        assertEquals(dataStructures.title, (upcoming as NextClass).name)
    }

    @Test
    fun upcomingClassAfter_choiceForLaterClass_stillShowsEarlierStart() {
        val earlier = entry(classId = 4, start = LocalTime(8, 0))
        val term = termWith(earlier, dataStructures)
        val choices = listOf(ConflictChoice(classIds = setOf(1, 4), frontClassId = 1))

        val upcoming = listOf(term).upcomingClassAfter(morningOf(wednesday), choices)

        assertEquals(earlier.title, (upcoming as NextClass).name)
    }

    private fun morningOf(date: LocalDate) = LocalDateTime(date, LocalTime(7, 0))

    private fun termWith(vararg entries: TimetableEntry) = TimetableTerm(
        id = "1",
        schoolYear = "2026-2027",
        number = "1",
        startDate = wednesday,
        endDate = wednesday,
        weeks = listOf(TimetableWeek(number = 1, dates = listOf(wednesday), entries = entries.toList())),
        isLoaded = true,
    )
}
