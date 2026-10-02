package com.qtekfun.fexplo.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.qtekfun.fexplo.MainActivity
import com.qtekfun.fexplo.R
import com.qtekfun.fexplo.core.model.TransferProgress
import com.qtekfun.fexplo.core.model.TransferStatus
import com.qtekfun.fexplo.core.util.formatBytes
import com.qtekfun.fexplo.domain.transfer.TransferState

/** Builds and posts the notifications of [FileTransferForegroundService]. */
class TransferNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.transfer_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
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
                context.getString(R.string.transfer_cancel),
                PendingIntent.getService(
                    context,
                    0,
                    Intent(context, FileTransferForegroundService::class.java)
                        .setAction(FileTransferForegroundService.ACTION_CANCEL),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        when {
            state.conflict != null -> builder
                .setContentText(context.getString(R.string.transfer_conflict_waiting))
                .setProgress(0, 0, true)
            progress == null || progress.fraction == null -> builder
                .setContentText(context.getString(R.string.transfer_preparing))
                .setProgress(0, 0, true)
            else -> {
                val percent = (progress.fraction!! * 100).toInt()
                builder
                    .setContentText(progress.currentName)
                    .setSubText(
                        context.getString(
                            R.string.transfer_progress_text,
                            percent,
                            formatBytes(progress.bytesPerSecond),
                        ),
                    )
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

    private fun baseBuilder(): NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setOnlyAlertOnce(true)
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
        private const val CHANNEL_ID = "transfers"
    }
}
