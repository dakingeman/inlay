package com.example.gridime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** D-pad speed (1.3): five steps, faster each way; Medium is exactly the pace a held D-pad always had, and it's kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DpadSpeedTest {
    @Test fun stepsAndDefault() {
        assertEquals("Medium is the old pace", Design.DPAD_REPEAT_MS, Design.dpadRepeat(KeyboardSettings().dpadSpeed))
        assertEquals(Design.DPAD_REPEAT_DELAY_MS, Design.dpadDelay(KeyboardSettings().dpadSpeed))
        for (i in 1..4) {
            assertTrue("faster each step", Design.dpadRepeat(i) < Design.dpadRepeat(i - 1))
            assertTrue("shorter wait each step", Design.dpadDelay(i) < Design.dpadDelay(i - 1))
        }
        assertEquals("out of range is clamped", Design.dpadRepeat(4), Design.dpadRepeat(9))
    }
    @Test fun savedAndRead() {
        val c = RuntimeEnvironment.getApplication()
        KeyboardSettings(dpadSpeed = 4).save(c)
        assertEquals(4, KeyboardSettings.load(c).dpadSpeed)
    }
}
