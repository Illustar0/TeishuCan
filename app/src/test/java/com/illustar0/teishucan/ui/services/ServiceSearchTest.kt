package com.illustar0.teishucan.ui.services

import com.illustar0.teishucan.domain.CampusService
import com.illustar0.teishucan.ui.component.SearchStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceSearchTest {
    @Test
    fun matchesServiceQuery_blankQuery_matchesAnyService() {
        assertTrue(matchesServiceQuery("", "成绩", "查询课程成绩"))
        assertTrue(matchesServiceQuery("   ", "成绩", "查询课程成绩"))
    }

    @Test
    fun matchesServiceQuery_singleToken_matchesTitleSummaryOrGroup() {
        assertTrue(matchesServiceQuery("空间", "空间预约", "预约座位与研讨间", "图书馆"))
        assertTrue(matchesServiceQuery("研讨间", "空间预约", "预约座位与研讨间", "图书馆"))
        assertTrue(matchesServiceQuery("图书馆", "空间预约", "预约座位与研讨间", "图书馆"))
    }

    @Test
    fun matchesServiceQuery_ignoresAsciiCase() {
        assertTrue(matchesServiceQuery("sso", "SSO 登录", "免登录进入"))
    }

    @Test
    fun matchesServiceQuery_multipleTokens_requireEveryTokenToMatchAcrossFields() {
        assertTrue(matchesServiceQuery("图书馆 座位", "空间预约", "预约座位与研讨间", "图书馆"))
        assertFalse(matchesServiceQuery("图书馆 成绩", "空间预约", "预约座位与研讨间", "图书馆"))
    }

    @Test
    fun searchStatus_onAnimationComplete_preservesQueryWhenExpandingAndClearsWhenCollapsing() {
        val expanding = SearchStatus(query = "成绩", phase = SearchStatus.Phase.Expanding)
        assertEquals(
            SearchStatus(query = "成绩", phase = SearchStatus.Phase.Expanded),
            expanding.onAnimationComplete(),
        )

        val collapsing = SearchStatus(query = "成绩", phase = SearchStatus.Phase.Collapsing)
        assertEquals(
            SearchStatus(query = "", phase = SearchStatus.Phase.Collapsed),
            collapsing.onAnimationComplete(),
        )
    }

    @Test
    fun serviceEntry_coversEveryWebAndNativeServiceExactlyOnce() {
        val webServices = ServiceEntry.entries.mapNotNull { (it.target as? ServiceTarget.Web)?.service }
        val nativeServices = ServiceEntry.entries.mapNotNull { (it.target as? ServiceTarget.Native)?.service }

        assertEquals(CampusService.entries.toSet(), webServices.toSet())
        assertEquals(CampusService.entries.size, webServices.size)
        assertEquals(NativeService.entries.toSet(), nativeServices.toSet())
        assertEquals(NativeService.entries.size, nativeServices.size)
        assertTrue(ServiceGroup.entries.all { it.entries.isNotEmpty() })
    }

    @Test
    fun availableClassrooms_staysInEasScheduleBeforeClassroomTimetable() {
        assertEquals(listOf(ServiceEntry.UndergradEas), ServiceGroup.Academic.entries)
        val scheduleEntries = ServiceGroup.EasSchedule.entries
        val availableIndex = scheduleEntries.indexOf(ServiceEntry.AvailableClassrooms)
        val timetableIndex = scheduleEntries.indexOf(ServiceEntry.ClassroomTimetable)
        assertTrue(availableIndex in 0..<timetableIndex)
    }

    @Test
    fun serviceOpener_openingWebService_tracksOpeningStateAndPublishesUrl() = runTest {
        val urlDeferred = CompletableDeferred<String>()
        val opener = ServiceOpener(this) { urlDeferred.await() }

        opener.open(ServiceTarget.Web(CampusService.Library)) {}
        runCurrent()
        assertEquals(CampusService.Library, opener.openingService.value)
        assertNull(opener.serviceUrl.url.value)

        urlDeferred.complete("https://library.example/sso")
        runCurrent()
        assertNull(opener.openingService.value)
        assertEquals("https://library.example/sso", opener.serviceUrl.url.value)
    }

    @Test
    fun serviceOpener_openingNativeWhileWebInFlight_cancelsWebAndNavigatesImmediately() = runTest {
        var wasCancelled = false
        val openedNative = mutableListOf<NativeService>()
        val opener = ServiceOpener(this) {
            try {
                awaitCancellation()
            } finally {
                wasCancelled = true
            }
        }

        opener.open(ServiceTarget.Web(CampusService.Library)) { openedNative += it }
        runCurrent()
        assertEquals(CampusService.Library, opener.openingService.value)

        opener.open(ServiceTarget.Native(NativeService.UndergradEas)) { openedNative += it }
        assertNull(opener.openingService.value)
        assertEquals(listOf(NativeService.UndergradEas), openedNative)

        runCurrent()
        assertTrue(wasCancelled)
        assertNull(opener.serviceUrl.url.value)
    }

    @Test
    fun serviceOpener_reopeningAnotherWebAfterCancel_keepsNewOpeningService() = runTest {
        val secondUrl = CompletableDeferred<String>()
        val opener = ServiceOpener(this) { service ->
            if (service == CampusService.Library) awaitCancellation() else secondUrl.await()
        }

        opener.open(ServiceTarget.Web(CampusService.Library)) {}
        runCurrent()
        opener.open(ServiceTarget.Native(NativeService.UndergradEas)) {}
        opener.open(ServiceTarget.Web(CampusService.Grades)) {}
        runCurrent()

        assertEquals(CampusService.Grades, opener.openingService.value)
        secondUrl.complete("https://grades.example/sso")
        runCurrent()
        assertNull(opener.openingService.value)
        assertEquals("https://grades.example/sso", opener.serviceUrl.url.value)
    }
}
