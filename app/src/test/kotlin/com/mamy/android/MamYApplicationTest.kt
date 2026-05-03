package com.mamy.android

import dagger.hilt.android.HiltAndroidApp
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MamYApplicationTest {

    @Test
    fun `MamYApplication is annotated with HiltAndroidApp`() {
        val annotation = MamYApplication::class.java.getAnnotation(HiltAndroidApp::class.java)
        assertNotNull(annotation, "MamYApplication must be annotated @HiltAndroidApp")
    }

    @Test
    fun `MamYApplication extends android Application`() {
        assertTrue(
            android.app.Application::class.java.isAssignableFrom(MamYApplication::class.java),
            "MamYApplication must extend android.app.Application"
        )
    }
}
