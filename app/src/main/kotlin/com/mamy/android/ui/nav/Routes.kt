package com.mamy.android.ui.nav

sealed interface Route {
    val route: String
}

object Routes {
    object Onboarding : Route { override val route = "onboarding" }
    object ReportsList : Route { override val route = "reports" }
    object Actions : Route { override val route = "actions" }
    object Settings : Route { override val route = "settings" }
    object NetworkLog : Route { override val route = "network_log" }
    object Data : Route { override val route = "data" }

    object PersonDetail : Route {
        const val ARG_PERSON_ID = "personId"
        override val route = "person/{$ARG_PERSON_ID}"
        fun path(personId: String) = "person/$personId"
    }

    object Briefing : Route {
        const val ARG_BRIEFING_ID = "briefingId"
        override val route = "briefing/{$ARG_BRIEFING_ID}"
        fun path(briefingId: String) = "briefing/$briefingId"
    }
}
