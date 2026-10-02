package com.qtekfun.fexplo.data.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.qtekfun.fexplo.domain.transfer.TransferServiceLauncher

class ServiceTransferLauncher(private val context: Context) : TransferServiceLauncher {
    override fun launch() {
        ContextCompat.startForegroundService(context, Intent(context, FileTransferForegroundService::class.java))
    }
}
