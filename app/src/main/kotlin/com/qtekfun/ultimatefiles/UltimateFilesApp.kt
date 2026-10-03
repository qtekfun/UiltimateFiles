package com.qtekfun.ultimatefiles

import android.app.Application
import com.qtekfun.ultimatefiles.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class UltimateFilesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@UltimateFilesApp)
            modules(appModule)
        }
    }
}
