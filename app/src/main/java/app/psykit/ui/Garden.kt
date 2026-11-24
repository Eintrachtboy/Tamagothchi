package app.psykit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.PetState
import app.psykit.core.model.PlotStage
import kotlin.math.sin

/** Beete: pflanzen, gemeinsam gießen, ernten. */
@Composable
fun GardenPanel(s: PetState, vm: PetViewModel) {
    val now = s.lastUpdate
    val today = PetEngine.epochDay(now, vm.repo.zone)
    Text("Pflanz Beeren und gieße sie mit ${s.name} an ${PetEngine.GARDEN_DAYS} Tagen. Nicht vergessen, sonst vertrocknen sie nach 3 Tagen ohne Wasser.", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for (i in 0 until 3) {
            val plot = s.plots.getOrNull(i) ?: app.psykit.core.model.Plot()
            val stage = PetEngine.plotStage(plot, now, vm.repo.zone)
            val wateredToday = plot.lastWateredDay == today
            Column(
                Modifier
                    .weight(1f)
                    .background(Color(0xFFE9DCC8), RoundedCornerShape(16.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stage.emoji, fontSize = 34.sp)
                Text(stage.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (stage in listOf(PlotStage.SEED, PlotStage.SPROUT, PlotStage.BUSH)) {
                    Text("💧 ${plot.waterDays}/${PetEngine.GARDEN_DAYS}" + if (wateredToday) " ✓" else "", style = MaterialTheme.typography.labelSmall)
                }
                when (stage) {
                    PlotStage.EMPTY, PlotStage.WITHERED -> FilledTonalButton(onClick = { vm.plant(i) }) { Text("Säen", maxLines = 1) }
                    PlotStage.RIPE -> Button(onClick = { vm.harvest(i) }) { Text("Ernten", maxLines = 1) }
                    else -> Button(onClick = { vm.water(i) }, enabled = !wateredToday && !s.sleeping) { Text("Gießen", maxLines = 1) }
                }
            }
        }
    }
    Text("🫐 Gartenbeeren im Vorrat: ${s.berryStock} (beim Füttern)", style = MaterialTheme.typography.bodySmall)
}

/** In der Szene: das Tierchen hilft mit der Gießkanne, Tropfen fallen, ein Pflänzchen sprießt. */
@Composable
fun BoxScope.WateringOverlay(trigger: Int) {
    val t = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        t.snapTo(0f); t.animateTo(1f, tween(1800, easing = LinearEasing))
    }
    if (t.value >= 1f) {
        Text("🌱", fontSize = 30.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 16.dp).alpha(0.8f))
        return
    }
    val p = t.value
    // Gießkanne neben dem Tierchen, leicht gekippt und wippend
    Text(
        "🫗", fontSize = 44.sp,
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x = 78.dp, y = (-10).dp + (sin(p * 12f) * 4f).dp)
            .rotate(-20f + sin(p * 10f) * 8f),
    )
    Canvas(Modifier.fillMaxSize()) {
        val sx = size.width * 0.5f + 100.dp.toPx()
        val sy = size.height * 0.5f + 10.dp.toPx()
        for (k in 0 until 10) {
            val ph = (p * 4f + k / 10f) % 1f
            val x = sx + (k % 3 - 1) * 8f + ph * 10f
            val y = sy + ph * (size.height * 0.4f)
            drawCircle(Color(0xFF5AB4F0).copy(alpha = 1f - ph * 0.6f), 5f, Offset(x, y))
        }
    }
    Text(
        if (p < 0.5f) "🌱" else "🌿", fontSize = (24 + p * 14).sp,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 16.dp),
    )
    Text("💦", fontSize = 22.sp, modifier = Modifier.align(Alignment.TopCenter).offset(x = 40.dp, y = (60 - p * 30).dp).alpha(1f - p))
}

/** Tagesaufgaben mit Fortschritt und Belohnung. */
@Composable
fun QuestPanel(s: PetState, vm: PetViewModel) {
    val quests = PetEngine.quests(s)
    Text("Tagesaufgaben", fontWeight = FontWeight.Bold)
    Text("Jeden Tag 3 neue Aufgaben. Schaffst du alle, gibt es einen Bonus.", style = MaterialTheme.typography.bodySmall)
    quests.forEach { q ->
        val prog = (s.questProgress[q.name] ?: 0).coerceAtMost(q.target)
        val done = prog >= q.target
        val claimed = q.name in s.questsClaimed
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((if (claimed) "✅ " else if (done) "🎁 " else "▫️ ") + q.label, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { prog.toFloat() / q.target },
                    modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 2.dp),
                    strokeCap = StrokeCap.Round,
                )
                Text("$prog / ${q.target} · +${q.rewardExp} EP", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
            if (claimed) Text("erledigt", style = MaterialTheme.typography.labelMedium)
            else Button(onClick = { vm.claimQuest(q) }, enabled = done) { Text("Abholen") }
        }
    }
}
