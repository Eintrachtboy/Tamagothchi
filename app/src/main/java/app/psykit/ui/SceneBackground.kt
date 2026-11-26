package app.psykit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Hintergrund-Landschaft der Szene als Vektorzeichnung (Runde 7): zwei Hügelreihen, in denen
 * die Dungeon-Höhle und der Garten-Wegweiser stehen. Zeichnung und Tippflächen nutzen dieselben Rechtecke.
 */
object SceneLandmarks {
    /** Höhle auf dem linken Hügel; Seitenverhältnis 200:160 wie die Vektorgrafik. */
    fun cave(w: Float, h: Float): Rect {
        val cw = w * 0.24f
        val bottom = h * 0.71f
        return Rect(w * 0.03f, bottom - cw * 0.8f, w * 0.03f + cw, bottom)
    }

    /** Wegweiser auf dem rechten Hügel (Runde 8, Variante b); Seitenverhältnis 60:40. */
    fun sign(w: Float, h: Float): Rect {
        val sw = w * 0.13f
        val bottom = h * 0.60f
        return Rect(w * 0.70f, bottom - sw * 40f / 60f, w * 0.70f + sw, bottom)
    }
}

/** Hügel hinter der Figur mit Farbverlauf und kleinen Bäumen (Runde 8). */
fun DrawScope.drawHills(night: Boolean) {
    val w = size.width; val h = size.height
    val farTop = if (night) Color(0xFF3B3578) else Color(0xFFCDEBBE)
    val farBottom = if (night) Color(0xFF2C2760) else Color(0xFFA9D79A)
    val midTop = if (night) Color(0xFF342F6C) else Color(0xFFBFE3AE)
    val midBottom = if (night) Color(0xFF282357) else Color(0xFF9CCD8C)
    drawOval(Brush.verticalGradient(listOf(farTop, farBottom), startY = h * 0.60f, endY = h * 0.90f),
        topLeft = Offset(-w * 0.18f, h * 0.60f), size = Size(w * 0.80f, h * 0.30f))
    drawOval(Brush.verticalGradient(listOf(midTop, midBottom), startY = h * 0.56f, endY = h * 0.86f),
        topLeft = Offset(w * 0.38f, h * 0.56f), size = Size(w * 0.85f, h * 0.30f))
    // kleine Bäume auf den Hügeln
    drawTree(Offset(w * 0.37f, h * 0.665f), h * 0.085f, night)
    drawTree(Offset(w * 0.45f, h * 0.675f), h * 0.06f, night)
    drawTree(Offset(w * 0.90f, h * 0.60f), h * 0.075f, night)
}

private fun DrawScope.drawTree(base: Offset, height: Float, night: Boolean) {
    val trunk = if (night) Color(0xFF3A2E52) else Color(0xFF8B6B4A)
    val leafDark = if (night) Color(0xFF2E5A4E) else Color(0xFF5FAE6B)
    val leafLight = if (night) Color(0xFF3A6E5E) else Color(0xFF7BC47F)
    val tw = height * 0.14f
    drawRoundRect(trunk, topLeft = Offset(base.x - tw / 2, base.y - height * 0.45f), size = Size(tw, height * 0.45f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(tw / 3))
    drawCircle(leafDark, height * 0.32f, Offset(base.x, base.y - height * 0.62f))
    drawCircle(leafLight, height * 0.22f, Offset(base.x - height * 0.10f, base.y - height * 0.72f))
}

/** Sonne mit Strahlen und zwei Wolken am Tag (Runde 8). */
fun DrawScope.drawDayDecor(sunColor: Color) {
    val w = size.width; val h = size.height
    val c = Offset(w * 0.83f, h * 0.17f)
    val r = h * 0.075f
    drawCircle(sunColor.copy(alpha = 0.30f), h * 0.11f, c)
    for (k in 0 until 10) {
        val a = k * (2.0 * PI / 10).toFloat()
        val from = Offset(c.x + cos(a) * r * 1.35f, c.y + sin(a) * r * 1.35f)
        val to = Offset(c.x + cos(a) * r * 1.85f, c.y + sin(a) * r * 1.85f)
        drawLine(sunColor.copy(alpha = 0.75f), from, to, strokeWidth = h * 0.007f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
    drawCircle(sunColor, r, c)
    drawCircle(Color.White.copy(alpha = 0.35f), r * 0.55f, Offset(c.x - r * 0.25f, c.y - r * 0.25f))
    drawCloud(Offset(w * 0.22f, h * 0.17f), h * 0.04f)
    drawCloud(Offset(w * 0.52f, h * 0.29f), h * 0.028f, alpha = 0.7f)
}

private fun DrawScope.drawCloud(center: Offset, r: Float, alpha: Float = 0.9f) {
    val c = Color.White.copy(alpha = alpha)
    val shade = Color(0xFFDCEAF7).copy(alpha = alpha)
    drawRoundRect(shade, topLeft = Offset(center.x - r * 1.9f, center.y + r * 0.15f), size = Size(r * 3.8f, r * 0.9f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.45f))
    drawCircle(c, r * 0.85f, Offset(center.x - r, center.y + r * 0.1f))
    drawCircle(c, r * 1.15f, Offset(center.x, center.y - r * 0.3f))
    drawCircle(c, r * 0.85f, Offset(center.x + r, center.y + r * 0.1f))
    drawRoundRect(c, topLeft = Offset(center.x - r * 1.8f, center.y), size = Size(r * 3.6f, r * 0.8f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.4f))
}

/** Blumen und Grasbüschel am Boden, an den Rändern (Runde 8). */
fun DrawScope.drawGroundDetails(night: Boolean) {
    val w = size.width; val h = size.height
    val grass = if (night) Color(0xFF3E6A5C) else Color(0xFF8CC583)
    val petals = if (night) listOf(Color(0xFF9B7BC9), Color(0xFFC78BAF), Color(0xFFC9B47B))
    else listOf(Color(0xFFFF8FB5), Color(0xFFFFD36E), Color(0xFFB79BFF))
    // Büschel
    listOf(0.07f to 0.90f, 0.18f to 0.95f, 0.84f to 0.92f, 0.94f to 0.88f).forEach { (fx, fy) ->
        val x = w * fx; val y = h * fy; val g = h * 0.03f
        drawLine(grass, Offset(x - g * 0.4f, y), Offset(x - g * 0.7f, y - g), strokeWidth = h * 0.006f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(grass, Offset(x, y), Offset(x, y - g * 1.2f), strokeWidth = h * 0.006f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(grass, Offset(x + g * 0.4f, y), Offset(x + g * 0.7f, y - g), strokeWidth = h * 0.006f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
    // Blumen
    listOf(0.12f to 0.89f, 0.22f to 0.93f, 0.80f to 0.94f, 0.90f to 0.91f).forEachIndexed { i, (fx, fy) ->
        val c = Offset(w * fx, h * fy); val pr = h * 0.012f
        val col = petals[i % petals.size]
        for (k in 0 until 5) {
            val a = k * (2.0 * PI / 5).toFloat()
            drawCircle(col, pr, Offset(c.x + cos(a) * pr * 1.2f, c.y + sin(a) * pr * 1.2f))
        }
        drawCircle(Color(0xFFFFF3B0), pr * 0.8f, c)
    }
}

/** Höhle und Wegweiser zeichnen (Vektorbilder), bei Nacht abgedunkelt. Der Wegweiser fehlt, solange die Werkzeugleiste rechts offen ist. */
fun DrawScope.drawLandmarks(cave: Painter, sign: Painter, night: Boolean, showSign: Boolean = true) {
    val w = size.width; val h = size.height
    val filter = if (night) ColorFilter.colorMatrix(
        ColorMatrix(floatArrayOf(
            0.55f, 0f, 0f, 0f, 0f,
            0f, 0.55f, 0f, 0f, 0f,
            0f, 0f, 0.75f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )),
    ) else null
    val c = SceneLandmarks.cave(w, h)
    translate(c.left, c.top) { with(cave) { draw(Size(c.width, c.height), colorFilter = filter) } }
    if (showSign) {
        val s = SceneLandmarks.sign(w, h)
        translate(s.left, s.top) { with(sign) { draw(Size(s.width, s.height), colorFilter = filter) } }
    }
}

/** Antippbare Flächen über Höhle und Wegweiser mit kleinen Beschriftungen. */
@Composable
fun BoxScope.LandmarkTargets(onDungeon: () -> Unit, onGarden: () -> Unit, showGarden: Boolean = true) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        @Composable
        fun target(r: Rect, label: String, onClick: () -> Unit) {
            // etwas größer als die Zeichnung, damit sie leicht zu treffen ist
            val pad = 6f * density.density
            val rr = Rect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad + 22f * density.density)
            Box(
                Modifier
                    .offset { IntOffset(rr.left.roundToInt(), rr.top.roundToInt()) }
                    .size(with(density) { rr.width.toDp() }, with(density) { rr.height.toDp() })
                    .clickable(onClickLabel = label, onClick = onClick),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF3D2E6B),
                    maxLines = 1,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                )
            }
        }
        target(SceneLandmarks.cave(w, h), "Dungeon", onDungeon)
        if (showGarden) target(SceneLandmarks.sign(w, h), "Garten", onGarden)
    }
}
