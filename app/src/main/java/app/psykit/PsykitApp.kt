package app.psykit

import android.app.Application
import app.psykit.notify.CareWorker

class PsykitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CareWorker.schedule(this)
    }
}
