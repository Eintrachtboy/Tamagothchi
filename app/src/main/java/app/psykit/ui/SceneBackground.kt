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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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

    /** Wegweiser auf dem Hügel rechts neben der Höhle; Seitenverhältnis 60:40. */
    fun sign(w: Float, h: Float): Rect {
        val sw = w * 0.13f
        val bottom = h * 0.68f
        return Rect(w * 0.25f, bottom - sw * 40f / 60f, w * 0.25f + sw, bottom)
    }
}

/** Hügel (Fernhügel, mittlere Hügel) hinter der Figur. Kommt vor dem Boden-Oval und hinter Höhle und Wegweiser. */
fun DrawScope.drawHills(night: Boolean) {
    val w = size.width; val h = size.height
    val far = if (night) Color(0xFF332E66) else Color(0xFFBFE3B0)
    val mid = if (night) Color(0xFF2C2760) else Color(0xFFB0DBA0)
    drawOval(far, topLeft = Offset(-w * 0.18f, h * 0.60f), size = Size(w * 0.80f, h * 0.30f))
    drawOval(mid, topLeft = Offset(w * 0.38f, h * 0.64f), size = Size(w * 0.85f, h * 0.30f))
}

/** Höhle und Wegweiser zeichnen (Vektorbilder), bei Nacht abgedunkelt. */
fun DrawScope.drawLandmarks(cave: Painter, sign: Painter, night: Boolean) {
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
    val s = SceneLandmarks.sign(w, h)
    translate(s.left, s.top) { with(sign) { draw(Size(s.width, s.height), colorFilter = filter) } }
}

/** Antippbare Flächen über Höhle und Wegweiser mit kleinen Beschriftungen. */
@Composable
fun BoxScope.LandmarkTargets(onDungeon: () -> Unit, onGarden: () -> Unit) {
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
        target(SceneLandmarks.sign(w, h), "Garten", onGarden)
    }
}
