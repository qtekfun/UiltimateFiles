package com.qtekfun.fexplo.di

import com.qtekfun.fexplo.domain.clipboard.ClipboardManager
import org.koin.dsl.module

val appModule = module {
    single { ClipboardManager() }
}
