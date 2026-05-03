package com.mamy.android.ui.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoutesTest {

    @Test
    fun `all 6 P1 routes are declared`() {
        val routes = listOf(
            Routes.Onboarding,
            Routes.ReportsList,
            Routes.PersonDetail,
            Routes.Actions,
            Routes.Settings,
            Routes.NetworkLog,
        )
        assertEquals(6, routes.size)
        assertEquals(6, routes.map { it.path }.toSet().size, "Routes paths must be unique")
    }

    @Test
    fun `PersonDetail route declares personId arg placeholder`() {
        assertTrue(
            Routes.PersonDetail.path.contains("{personId}"),
            "PersonDetail path must contain {personId} arg placeholder"
        )
    }
}
