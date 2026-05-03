package com.mamy.android.domain.intent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * P2 stub — always returns [Intent.Capture]. P4 will replace with full FR+EN grammar.
 */
@Singleton
class IntentRouter @Inject constructor() {
    fun route(transcript: String): Intent = Intent.Capture(transcript)
}
