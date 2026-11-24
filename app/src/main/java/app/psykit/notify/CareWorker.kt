package app.psykit.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.psykit.MainActivity
import app.psykit.R
import app.psykit.core.engine.DayPhase
import app.psykit.core.model.Mood
import app.psykit.core.model.Stage
import app.psykit.data.PetRepository
import app.psykit.steps.StepSensor
import java.util.concurrent.TimeUnit

/**
 * Läuft etwa stündlich im Hintergrund: zählt Schritte nach, rechnet die Zeit weiter
 * und erinnert per Benachrichtigung, wenn das Tierchen etwas braucht (nicht nachts).
 */
class CareWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = PetRepository.get(applicationContext)
        StepSensor(applicationContext).readOnce()?.let { repo.onStepCounter(it) }
        repo.refresh()
        val now = System.currentTimeMillis()
        val night = DayPhase.at(now, repo.zone).isNight
        if (night || now - repo.lastNotificationAt < 3 * 3_600_000L) return Result.success()

        // Alle Tierchen prüfen, das erste, das etwas braucht, meldet sich
        val text = repo.allPets().map { it.second }.filter { it.stage != Stage.EGG }.firstNotNullOfOrNull { s ->
            when (s.mood) {
                Mood.SICK -> "${s.name} fühlt sich gar nicht gut. Kümmere dich um ${s.name}!"
                Mood.HUNGRY -> "${s.name} hat Hunger!"
                Mood.DIRTY -> "Bei ${s.name} müsste mal sauber gemacht werden."
                Mood.SAD -> "${s.name} ist einsam und möchte spielen."
                else -> null
            }
        } ?: return Result.success()

        if (notify(applicationContext, text)) repo.lastNotificationAt = now
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "pflege"
        private const val WORK = "psykit-care"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<CareWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun notify(ctx: Context, text: String): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return false
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pflege-Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT))
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Psykit")
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            return runCatching { NotificationManagerCompat.from(ctx).notify(1, n); true }.getOrDefault(false)
        }
    }
}
