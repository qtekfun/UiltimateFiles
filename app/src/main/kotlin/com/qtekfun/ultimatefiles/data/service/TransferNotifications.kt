package com.qtekfun.ultimatefiles.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.qtekfun.ultimatefiles.MainActivity
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.TransferProgress
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.core.util.formatBytes
import com.qtekfun.ultimatefiles.core.util.formatDuration
import com.qtekfun.ultimatefiles.domain.transfer.TransferState

/** Builds and posts the notifications of [FileTransferForegroundService]. */
class TransferNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        // Channel settings cannot be changed after creation, so a different importance needs a new channel.
        OLD_CHANNEL_IDS.forEach(manager::deleteNotificationChannel)
        manager.createNotificationChannel(
            // Default importance, because many lock screens only list low-importance (silent) notifications when the
            // user opted in; the channel itself makes no sound, vibration or badge, and updates never alert again.
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.transfer_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    /** Ongoing notification: current file, percentage and estimated speed. */
    fun running(state: TransferState): Notification {
        val progress = state.progress
        val builder = baseBuilder()
            .setContentTitle(context.getString(R.string.transfer_title))
            .setOngoing(true)
            .addAction(
                0,
                context.getString(if (state.paused) R.string.transfer_resume else R.string.transfer_pause),
                serviceAction(1, FileTransferForegroundService.ACTION_TOGGLE_PAUSE),
            )
            .addAction(
                0,
                context.getString(R.string.transfer_cancel),
                serviceAction(0, FileTransferForegroundService.ACTION_CANCEL),
            )
        when {
            state.paused -> builder
                .setContentTitle(context.getString(R.string.transfer_paused))
                .setContentText(progress?.currentName ?: context.getString(R.string.transfer_preparing))
                .setProgress(100, ((progress?.fraction ?: 0f) * 100).toInt(), false)
            state.conflict != null -> builder
                .setContentText(context.getString(R.string.transfer_conflict_waiting))
                .setProgress(0, 0, true)
            progress == null || progress.fraction == null -> builder
                .setContentText(context.getString(R.string.transfer_preparing))
                .setProgress(0, 0, true)
            else -> {
                val percent = (progress.fraction!! * 100).toInt()
                val details = listOfNotNull(
                    context.getString(R.string.transfer_progress_text, percent, formatBytes(progress.bytesPerSecond)),
                    progress.remainingSeconds?.let { context.getString(R.string.transfer_remaining, formatDuration(it)) },
                    context.getString(R.string.transfer_verifying).takeIf { progress.status == TransferStatus.VERIFYING },
                )
                builder
                    .setContentText(progress.currentName)
                    .setSubText(details.joinToString(" · "))
                    .setProgress(100, percent, false)
            }
        }
        if (state.queued > 0) {
            builder.setNumber(state.queued + 1)
        }
        return builder.build()
    }

    fun showResult(progress: TransferProgress?) {
        val (title, text) = when (progress?.status) {
            TransferStatus.COMPLETED -> R.string.transfer_done to null
            TransferStatus.CANCELLED -> R.string.transfer_cancelled to null
            else -> R.string.transfer_failed to progress?.error
        }
        val notification = baseBuilder()
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        manager.notify(RESULT_ID, notification)
    }

    fun update(state: TransferState) {
        manager.notify(ONGOING_ID, running(state))
    }

    private fun serviceAction(requestCode: Int, action: String): PendingIntent = PendingIntent.getService(
        context,
        requestCode,
        Intent(context, FileTransferForegroundService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun baseBuilder(): NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // progress and the pause/cancel buttons on the lock screen
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )

    companion object {
        const val ONGOING_ID = 1
        const val RESULT_ID = 2
        private val OLD_CHANNEL_IDS = listOf("transfers", "transfers_lockscreen")
        private const val CHANNEL_ID = "transfers_progress"
    }
}
