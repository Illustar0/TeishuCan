package com.illustar0.chibacan.data

import com.illustar0.chibacan.domain.MeterOption
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationParentTest {
    private fun path(vararg ids: String) = ids.map { MeterOption(it, name = "") }

    @Test
    fun locationParent_noSelection_queriesTheTopLevel() {
        assertEquals("", locationParent(path()))
    }

    @Test
    fun locationParent_area_isItsOwnId() {
        assertEquals("1", locationParent(path("1")))
    }

    @Test
    fun locationParent_building_includesTheArea() {
        assertEquals("1-2", locationParent(path("1", "2")))
    }

    @Test
    fun locationParent_level_includesAreaAndBuilding() {
        assertEquals("1-2--3", locationParent(path("1", "2", "3")))
    }
}
