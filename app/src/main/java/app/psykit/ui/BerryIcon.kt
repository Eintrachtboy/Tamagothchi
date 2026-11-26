package app.psykit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.psykit.core.model.Berry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Farbe der Beere im Symbol: Blau, Gelb, Pink, Gold (Runde 12: jede Sorte sieht wie eine Beere aus). */
private fun berryFill(b: Berry): Color = when (b) {
    Berry.BLUE -> Color(0xFF4A6FD8)
    Berry.STAR -> Color(0xFFF2C230)
    Berry.PSI -> Color(0xFFE05CB5)
    Berry.GOLD -> Color(0xFFFFB300)
}

/** Gezeichnete Beere: drei runde Früchte mit Glanzlicht und Blatt, je Sorte mit eigener Farbe und Kennzeichen. */
@Composable
fun BerryIcon(berry: Berry, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawBerry(berry) }
}

private fun DrawScope.drawBerry(b: Berry) {
    val w = size.width; val h = size.height
    val c = berryFill(b)
    val dark = Color(c.red * 0.7f, c.green * 0.7f, c.blue * 0.7f)
    val r = w * 0.27f
    val spots = listOf(Offset(w * 0.34f, h * 0.62f), Offset(w * 0.66f, h * 0.62f), Offset(w * 0.5f, h * 0.38f))
    // Gold: leuchtender Ring
    if (b == Berry.GOLD) drawCircle(Color(0x55FFE082), w * 0.5f, Offset(w * 0.5f, h * 0.52f))
    spots.forEach { p ->
        drawCircle(dark, r, p + Offset(0f, h * 0.025f))
        drawCircle(c, r, p)
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.28f, p + Offset(-r * 0.35f, -r * 0.35f))
    }
    // Blatt oben
    val leaf = Path().apply {
        moveTo(w * 0.5f, h * 0.20f)
        quadraticBezierTo(w * 0.72f, h * 0.02f, w * 0.88f, h * 0.18f)
        quadraticBezierTo(w * 0.70f, h * 0.30f, w * 0.5f, h * 0.20f)
        close()
    }
    drawPath(leaf, Color(0xFF4CAF50))
    when (b) {
        Berry.STAR -> drawStar(Offset(w * 0.5f, h * 0.40f), r * 0.7f, Color.White.copy(alpha = 0.9f))
        Berry.PSI -> {
            // kleine Funken
            drawCircle(Color.White.copy(alpha = 0.9f), w * 0.04f, Offset(w * 0.18f, h * 0.30f))
            drawCircle(Color.White.copy(alpha = 0.9f), w * 0.03f, Offset(w * 0.84f, h * 0.76f))
        }
        Berry.GOLD -> drawStar(Offset(w * 0.5f, h * 0.40f), r * 0.55f, Color(0xFFFFF8E1))
        else -> {}
    }
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val a = (-PI / 2 + i * PI / 5).toFloat()
        val rr = if (i % 2 == 0) radius else radius * 0.45f
        val x = center.x + cos(a) * rr; val y = center.y + sin(a) * rr
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}
