package net.marvinweber.simsli

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import net.marvinweber.simsli.data.sync.RealtimeObserver
import net.marvinweber.simsli.data.sync.SyncManager
import javax.inject.Inject

@HiltAndroidApp
class SimsliApp : Application() {

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var realtimeObserver: RealtimeObserver

    override fun onCreate() {
        super.onCreate()
        syncManager.start()
        realtimeObserver.start()
    }
}
