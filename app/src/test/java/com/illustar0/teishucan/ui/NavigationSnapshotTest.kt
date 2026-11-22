package com.illustar0.teishucan.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NavigationSnapshotTest {
    @Test
    fun roundTrip_preservesActiveTabAndBackStack() {
        val snapshot = NavigationSnapshot(
            routes = listOf(Route.Main, Route.About, Route.OpenSourceLicenses),
            destination = AppDestination.Settings,
        )

        assertEquals(snapshot, Json.decodeFromString<NavigationSnapshot>(Json.encodeToString(snapshot)))
    }

    @Test
    fun snapshot_rejectsBackStackWithoutRoot() {
        assertThrows(IllegalArgumentException::class.java) {
            NavigationSnapshot(routes = listOf(Route.About))
        }
    }

    @Test
    fun snapshot_rejectsRepeatedRoutes() {
        assertThrows(IllegalArgumentException::class.java) {
            NavigationSnapshot(routes = listOf(Route.Main, Route.About, Route.About))
        }
    }
}
