package com.mamy.android.ui.nav

sealed class Routes(val path: String) {
    data object Onboarding : Routes("onboarding")
    data object ReportsList : Routes("reports")
    data object PersonDetail : Routes("reports/{personId}") {
        fun build(personId: String): String = "reports/$personId"
    }
    data object Actions : Routes("actions")
    data object Settings : Routes("settings")
    data object NetworkLog : Routes("settings/network-log")
    // Added by W1-C wave1-ui-3: data + sms history sub-routes
    data object Data : Routes("settings/data")
    data object SmsHistory : Routes("data/sms-history")
}
