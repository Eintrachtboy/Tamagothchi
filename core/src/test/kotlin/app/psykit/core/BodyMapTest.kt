package app.psykit.core

import app.psykit.core.model.BodyMap
import app.psykit.core.model.BodyPoint
import app.psykit.core.model.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodyMapTest {
    /**
     * Testfigur 20×20: Kopf oben in der Mitte (Spalten 6–13, Zeilen 2–8),
     * Körper darunter (Spalten 5–14, Zeilen 9–17), Schwanz nach rechts (Spalten 15–19, Zeilen 13–15).
     * Alles andere ist transparent.
     */
    private fun figure(): BodyMap {
        val c = 20; val r = 20
        val m = BooleanArray(c * r)
        fun fill(x0: Int, x1: Int, y0: Int, y1: Int) { for (y in y0..y1) for (x in x0..x1) m[y * c + x] = true }
        fill(6, 13, 2, 8); fill(5, 14, 9, 17); fill(15, 19, 13, 15)
        return BodyMap(c, r, m, headX = 0.5f, headTop = 0.1f, headWidth = 0.4f, eyesY = 0.25f)
    }

    @Test fun transparentAreaIsNoZone() {
        val b = figure()
        assertNull(b.zoneAt(BodyPoint(0.05f, 0.05f)), "leere Ecke darf nicht zählen")
        assertNull(b.zoneAt(BodyPoint(0.9f, 0.3f)), "neben dem Kopf ist leer")
    }

    @Test fun visiblePartsHitTheirZones() {
        val b = figure()
        assertEquals(Zone.HEAD, b.zoneAt(BodyPoint(0.5f, 0.16f)))
        assertEquals(Zone.BELLY, b.zoneAt(BodyPoint(0.5f, 0.7f)))
        assertEquals(Zone.TAIL, b.zoneAt(BodyPoint(0.95f, 0.72f)), "Schwanz wird automatisch aus dem Umriss gefunden")
    }

    @Test fun mouthAnchorDecidesFeeding() {
        val auto = figure()
        assertTrue(auto.mouthHit(auto.mouth))
        assertFalse(auto.mouthHit(BodyPoint(0.5f, 0.85f)), "Bauch ist nicht der Mund")
        val c = 20; val m = BooleanArray(c * c) { true }
        val custom = BodyMap(c, c, m, 0.5f, 0.1f, 0.4f, 0.25f, mouth = BodyPoint(0.3f, 0.6f))
        assertTrue(custom.mouthHit(BodyPoint(0.32f, 0.62f)))
        assertFalse(custom.mouthHit(BodyPoint(0.5f, 0.1f)), "oben am Kopf ist nicht automatisch der Mund")
    }

    @Test fun hintsFollowRealZoneCenters() {
        val b = figure()
        assertTrue(b.hint(Zone.EARS, Zone.TAIL).contains("unten"))
        assertTrue(b.hint(Zone.BELLY, Zone.HEAD).contains("oben"))
        // Schwanz liegt rechts auf Bauchhöhe: Tipp muss nach unten oder rechts zeigen, nicht nur „unten“ ins Leere
        val h = b.hint(Zone.BELLY, Zone.TAIL)
        assertTrue(h.contains("rechts") || h.contains("unten"), h)
    }

    @Test fun dirtSpotsLieOnTheBody() {
        val b = figure()
        b.bodyPoints(12, 42L).forEach { assertTrue(b.visible(it), "Fleck auf leerer Fläche: $it") }
    }
}
