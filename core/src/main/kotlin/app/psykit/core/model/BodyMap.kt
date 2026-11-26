package app.psykit.core.model

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Punkt in Bildanteilen (0..1, links oben = 0,0). */
data class BodyPoint(val x: Float, val y: Float)

/**
 * Körperstellen eines eigenen Bildes (Befund 06).
 *
 * Grundlage ist die Maske der sichtbaren Pixel: Transparente Stellen gehören nie zum Tierchen.
 * Kopf, Mund und Schwanz kommen aus Ankerpunkten (pro Figurenpaket einstellbar); fehlen Mund oder
 * Schwanz, werden sie aus Kopf und Umriss abgeleitet. Jede sichtbare Stelle gehört zur nächstgelegenen Zone.
 *
 * @param mask sichtbare Pixel als Raster [cols] × [rows], zeilenweise von oben
 * @param headX Kopfmitte, @param headTop Oberkante des Kopfes, @param headWidth Kopfbreite, @param eyesY Augenhöhe
 */
class BodyMap(
    val cols: Int,
    val rows: Int,
    private val mask: BooleanArray,
    headX: Float,
    headTop: Float,
    headWidth: Float,
    eyesY: Float,
    mouth: BodyPoint? = null,
    tail: BodyPoint? = null,
) {
    init { require(mask.size == cols * rows && cols > 0 && rows > 0) }

    /** Umriss der sichtbaren Pixel (Bildanteile). */
    val left: Float; val right: Float; val top: Float; val bottom: Float

    init {
        var l = cols; var r = -1; var t = rows; var b = -1
        for (y in 0 until rows) for (x in 0 until cols) if (mask[y * cols + x]) {
            l = min(l, x); r = max(r, x); t = min(t, y); b = max(b, y)
        }
        if (r < 0) { left = 0f; right = 1f; top = 0f; bottom = 1f }
        else { left = l.toFloat() / cols; right = (r + 1f) / cols; top = t.toFloat() / rows; bottom = (b + 1f) / rows }
    }

    val mouth: BodyPoint = mouth ?: BodyPoint(headX, (eyesY + max(0.04f, (eyesY - headTop)) * 0.8f).coerceAtMost(bottom))
    val tail: BodyPoint = tail ?: autoTail(headX)

    /** Mittelpunkte je Zone; Ohren und Wangen haben zwei Seiten. */
    val centers: Map<Zone, List<BodyPoint>> = run {
        val half = headWidth / 2f
        val chinY = min(bottom, this.mouth.y + 0.06f)
        mapOf(
            Zone.EARS to listOf(BodyPoint(headX - half * 0.75f, headTop + 0.02f), BodyPoint(headX + half * 0.75f, headTop + 0.02f)),
            Zone.HEAD to listOf(BodyPoint(headX, (headTop + eyesY) / 2f)),
            Zone.CHEEKS to listOf(BodyPoint(headX - half * 0.7f, (eyesY + this.mouth.y) / 2f), BodyPoint(headX + half * 0.7f, (eyesY + this.mouth.y) / 2f)),
            Zone.CHIN to listOf(BodyPoint(this.mouth.x, chinY)),
            Zone.BELLY to listOf(BodyPoint((left + right) / 2f, (chinY + bottom) / 2f + (bottom - chinY) * 0.05f)),
            Zone.TAIL to listOf(this.tail),
        )
    }

    /** Mitte einer Zone (bei zwei Seiten der Mittelwert, für Richtungstipps). */
    fun center(z: Zone): BodyPoint = centers.getValue(z).let { ps -> BodyPoint(ps.map { it.x }.average().toFloat(), ps.map { it.y }.average().toFloat()) }

    fun visible(p: BodyPoint): Boolean {
        val x = (p.x * cols).toInt(); val y = (p.y * rows).toInt()
        if (x !in 0 until cols || y !in 0 until rows) return false
        return mask[y * cols + x]
    }

    /** Zone unter dem Punkt oder null, wenn dort kein sichtbarer Teil des Tierchens ist. */
    fun zoneAt(p: BodyPoint): Zone? {
        if (!visible(p)) return null
        return centers.entries.minByOrNull { (_, ps) -> ps.minOf { hypot(it.x - p.x, it.y - p.y) } }?.key
    }

    /** Landet Futter am Mund? [tolerance] in Bildanteilen. */
    fun mouthHit(p: BodyPoint, tolerance: Float = 0.12f): Boolean = hypot(p.x - mouth.x, p.y - mouth.y) <= tolerance

    /** Richtungstipp von einer Stelle zur gesuchten, aus den echten Zonenmitten (K3). */
    fun hint(from: Zone, to: Zone): String {
        val a = center(from); val b = center(to)
        val dy = b.y - a.y; val dx = b.x - a.x
        val h = (bottom - top).coerceAtLeast(0.1f)
        return when {
            dy <= -0.3f * h -> "Viel weiter oben!"
            dy < -0.1f * h -> "Etwas weiter oben."
            dy >= 0.3f * h -> "Viel weiter unten!"
            dy > 0.1f * h -> "Etwas weiter unten."
            abs(dx) > 0.15f -> if (dx < 0) "Ganz nah dran! Etwas weiter links." else "Ganz nah dran! Etwas weiter rechts."
            to == Zone.EARS || to == Zone.CHEEKS -> "Ganz nah dran! Eher seitlich."
            else -> "Ganz nah dran! Eher in der Mitte."
        }
    }

    /** Sichtbare Punkte für Schmutzflecken: auf dem Körper, nicht auf leerer Bildfläche. */
    fun bodyPoints(count: Int, seed: Long): List<BodyPoint> {
        val cells = (0 until cols * rows).filter { i ->
            mask[i] && (i / cols).toFloat() / rows > (top + (bottom - top) * 0.25f)
        }.ifEmpty { (0 until cols * rows).filter { mask[it] } }
        if (cells.isEmpty()) return emptyList()
        val r = kotlin.random.Random(seed)
        return List(count) { val i = cells[r.nextInt(cells.size)]; BodyPoint((i % cols + 0.5f) / cols, (i / cols + 0.5f) / rows) }
    }

    /** Schwanz automatisch: der sichtbare Punkt im unteren Teil, der am weitesten von der Kopfmitte entfernt ist. */
    private fun autoTail(headX: Float): BodyPoint {
        var best: BodyPoint? = null; var bestD = -1f
        val fromRow = ((top + (bottom - top) * 0.45f) * rows).toInt().coerceIn(0, rows - 1)
        for (y in fromRow until rows) for (x in 0 until cols) if (mask[y * cols + x]) {
            val px = (x + 0.5f) / cols
            val d = abs(px - headX)
            if (d > bestD) { bestD = d; best = BodyPoint(px, (y + 0.5f) / rows) }
        }
        return best ?: BodyPoint((left + right) / 2f, bottom)
    }
}
