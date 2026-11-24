package com.illustar0.teishucan.ui.services

import com.illustar0.teishucan.ui.component.SearchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
