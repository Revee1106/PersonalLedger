package com.eric.personalledger

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationCapabilitiesTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun installedAppDoesNotExposeAccessibilityServices() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val services = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SERVICES).services.orEmpty()
        assertTrue(
            "The installed app must not register a service that can read other apps' pages",
            services.none { it.permission == Manifest.permission.BIND_ACCESSIBILITY_SERVICE },
        )
    }

    @Test fun settingsDoesNotOfferPageReading() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("账").performClick()
            compose.onNodeWithText("付款页面读取验证").assertDoesNotExist()
        }
    }
}
