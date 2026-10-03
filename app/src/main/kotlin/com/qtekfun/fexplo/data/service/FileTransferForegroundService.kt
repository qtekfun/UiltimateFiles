package com.qtekfun.fexplo.data.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.qtekfun.fexplo.core.model.TransferProgress
import com.qtekfun.fexplo.core.model.TransferStatus
import com.qtekfun.fexplo.domain.transfer.TransferCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Keeps long copies/moves alive in the background and mirrors their progress in a notification. */
class FileTransferForegroundService : Service(), KoinComponent {
    private val coordinator: TransferCoordinator by inject()
    private val notifications: TransferNotifications by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var lastStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Must be promoted to foreground right away, even if there turns out to be nothing to do.
        ServiceCompat.startForeground(
            this,
            TransferNotifications.ONGOING_ID,
            notifications.running(coordinator.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        when (intent?.action) {
            ACTION_CANCEL -> coordinator.cancelAll()
            ACTION_TOGGLE_PAUSE -> coordinator.togglePause()
        }
        if (coordinator.claimWorker()) {
            scope.launch { drainQueue() }
        } else if (coordinator.isIdle()) {
            finish(null)
        }
        return START_NOT_STICKY
    }

    /** Keeps the CPU running while a long copy goes on with the screen off. */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fexplo:transfer")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private suspend fun drainQueue() {
        acquireWakeLock()
        val updates = coordinator.state.onEach { notifications.update(it) }.launchIn(scope)
        var last: TransferProgress? = null
        while (true) {
            val request = coordinator.next() ?: break
            last = coordinator.run(request) ?: last
        }
        updates.cancel()
        finish(last)
    }

    private fun finish(result: TransferProgress?) {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (result != null) notifications.showResult(result)
        stopSelf(lastStartId)
    }

    /**
     * Android 15 stops `dataSync` foreground services after six hours. End the transfer cleanly
     * (it shows up as cancelled in the history) instead of letting the system kill the process.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        coordinator.cancelAll()
        finish(coordinator.state.value.progress?.copy(status = TransferStatus.CANCELLED))
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 12L * 60 * 60 * 1000
        const val ACTION_CANCEL = "com.qtekfun.fexplo.action.CANCEL_TRANSFER"
        const val ACTION_TOGGLE_PAUSE = "com.qtekfun.fexplo.action.TOGGLE_PAUSE_TRANSFER"
    }
}
