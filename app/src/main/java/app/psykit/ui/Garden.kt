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
