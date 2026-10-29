package com.example.gridime

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The device pictures come out of a pipeline (build.sh: kitgen.js, kit2android.py, geo.js, room.py). If a step is
 * skipped, the pictures still show but nothing lights properly: no press ring, no moving sticks.
 * This checks every picture's data is complete, so a skipped step fails the build instead of shipping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceAssetsTest {
    private val dir = File("src/main/assets/devices")

    @Test fun everyPictureHasItsMeasurements() {
        val files = dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        assertTrue("no device pictures found in $dir", files.size >= 13)
        val problems = mutableListOf<String>()
        for (f in files) {
            val css = JSONObject(f.readText())
            val geo = css.optJSONObject("_geo")
            if (geo == null) { problems += "${f.name}: no _geo (run geo.js)"; continue }
            for (key in css.keys()) {
                if (key.startsWith("_")) continue
                val g = geo.optJSONObject(key)
                if (g == null) { problems += "${f.name}: $key can light but has no measurements"; continue }
                if (g.optDouble("w", 0.0) <= 0 || g.optDouble("h", 0.0) <= 0) problems += "${f.name}: $key has no size"
                val isArm = key in setOf("Dup", "Ddown", "Dleft", "Dright")
                if (!isArm && !g.has("ring")) problems += "${f.name}: $key has no press ring"
                if ((key == "LS" || key == "RS") && !g.has("stick")) problems += "${f.name}: $key has no stick data"
                if (!g.has("stick") && !g.has("room")) problems += "${f.name}: $key has no room measured (run room.py)"
            }
            // a colour the kit couldn't find (a missing token) shows up as "undefined" or "NaN": never ship that
            val svgText = File(dir, f.name.replace(".json", ".svg")).takeIf { it.exists() }?.readText().orEmpty()
            for (bad in listOf("undefined", "NaN", "null")) if (bad in svgText || "\"$bad\"" in f.readText() || ":$bad" in f.readText()) problems += "${f.name}: contains $bad (a colour or size the kit couldn't find)"
            // every picture file has its drawing next to it
            if (!File(dir, f.name.replace(".json", ".svg")).exists()) problems += "${f.name}: no matching .svg"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
