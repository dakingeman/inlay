package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Konami reward and the UX laws (8 Nov): its quiet words read (4.5:1), and "Remove animations" gets the calm version. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SecretCalmTest {
    @Test fun quietWordsRead() {
        val ratio = Contrast.ratio(Design.SECRET_GREY, Design.SECRET_INK)
        assertTrue("grey words on the screen's black: $ratio", ratio >= 4.5)
    }

    private fun frame(v: SecretScreen, t: Float): Bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).also { v.scene(Canvas(it), 120, 80, t) }

    @Test fun calmMeansNoMovement() {
        val v = SecretScreen(RuntimeEnvironment.getApplication())
        // within the first theme's moment and within the intro, two times a quarter second apart
        val theme = SecretScreen.INTRO_S + 0.1f; val intro = 0.3f
        v.calm = true
        assertTrue("calm: the theme is simply there (no wipe, no glide)", frame(v, theme).sameAs(frame(v, theme + 0.25f)))
        assertTrue("calm: the stars stand still", frame(v, intro).sameAs(frame(v, intro + 0.25f)))
        v.calm = false
        assertFalse("with animations the theme wipes in", frame(v, theme).sameAs(frame(v, theme + 0.25f)))
    }
}
