package com.qtekfun.fexplo

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FexploApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FexploApp)
        }
    }
}
