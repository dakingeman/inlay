package com.example.gridime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

/** Automatic recognises gaming handhelds by name, even when their built-in controls report as external. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class DeviceDetectionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun device(maker: String, model: String, brand: String = maker) {
        ShadowBuild.setManufacturer(maker); ShadowBuild.setBrand(brand); ShadowBuild.setModel(model)
        ShadowBuild.setDevice(model.lowercase().replace(" ", "_")); ShadowBuild.setProduct(model.lowercase().replace(" ", "_"))
    }

    private fun auto() = Controls.of(context, KeyboardSettings(picture = PictureChoice.AUTO, verticalSticks = true))

    @Test fun odin2PortalIsAWideHandheld() {
        device("AYN", "Odin2 Portal")
        assertEquals(DeviceKind.WIDE, auto().kind)
        assertEquals(true, auto().sticks)
    }

    @Test fun retroidPocket5IsAWideHandheld() {
        device("Retroid", "Retroid Pocket 5")
        assertEquals(DeviceKind.WIDE, auto().kind)
    }

    @Test fun retroidPocketClassicIsVerticalWithoutSticks() {
        device("Retroid", "Retroid Pocket Classic")
        val c = auto()
        assertEquals(DeviceKind.VERTICAL, c.kind)
        assertEquals(false, c.sticks)                  // even though the setting says sticks
    }

    @Test fun anbernicRg405vIsVerticalWithSticks() {
        device("Anbernic", "RG 405V")
        val c = Controls.of(context, KeyboardSettings(picture = PictureChoice.AUTO, verticalSticks = false))
        assertEquals(DeviceKind.VERTICAL, c.kind)
        assertEquals(true, c.sticks)
    }

    @Test fun aPhoneIsNotTakenForAHandheld() {
        device("Google", "Pixel 8")
        assertNull(DevicePicture.knownHandheld(context))
    }

    @Test fun aWordInsideAnotherNeverCounts() {
        device("Raynor", "Odinson Phone", brand = "Gapdgen")   // "ayn", "odin", "gpd" only inside other words
        assertNull(DevicePicture.knownHandheld(context))
    }

    @Test fun choosingYourselfStillWins() {
        device("AYN", "Odin2 Portal")
        assertEquals(DeviceKind.CONTROLLER, Controls.of(context, KeyboardSettings(picture = PictureChoice.CONTROLLER)).kind)
    }
}
