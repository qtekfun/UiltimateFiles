package com.qtekfun.fexplo.di

import android.os.Environment
import com.qtekfun.fexplo.R
import com.qtekfun.fexplo.data.io.FileStreamCopier
import com.qtekfun.fexplo.data.repository.LocalFileSystemRepository
import com.qtekfun.fexplo.data.repository.RoutingFileSystemRepository
import com.qtekfun.fexplo.data.repository.SafFileSystemRepository
import com.qtekfun.fexplo.data.service.ServiceTransferLauncher
import com.qtekfun.fexplo.data.service.TransferNotifications
import com.qtekfun.fexplo.domain.clipboard.ClipboardManager
import com.qtekfun.fexplo.domain.repository.FileSystemRepository
import com.qtekfun.fexplo.domain.transfer.TransferCoordinator
import com.qtekfun.fexplo.domain.transfer.TransferServiceLauncher
import com.qtekfun.fexplo.domain.usecase.BatchCopyUseCase
import com.qtekfun.fexplo.domain.usecase.BatchMoveUseCase
import com.qtekfun.fexplo.domain.usecase.BuildBreadcrumbUseCase
import com.qtekfun.fexplo.domain.usecase.DeleteUseCase
import com.qtekfun.fexplo.domain.usecase.HashCalcUseCase
import com.qtekfun.fexplo.domain.usecase.StreamCopier
import com.qtekfun.fexplo.domain.usecase.TransferEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { ClipboardManager() }

    single<StreamCopier> { FileStreamCopier() }
    single {
        @Suppress("DEPRECATION")
        LocalFileSystemRepository(
            root = Environment.getExternalStorageDirectory(),
            label = androidContext().getString(R.string.storage_internal),
        )
    }
    single { SafFileSystemRepository(androidContext()) }
    single<FileSystemRepository> {
        RoutingFileSystemRepository(local = get<LocalFileSystemRepository>(), saf = get<SafFileSystemRepository>())
    }

    single { TransferEngine(repository = get(), copier = get()) }
    single<TransferServiceLauncher> { ServiceTransferLauncher(androidContext()) }
    single { TransferCoordinator(engine = get(), launcher = get()) }
    single { TransferNotifications(androidContext()) }

    factory { BatchCopyUseCase(get()) }
    factory { BatchMoveUseCase(get()) }
    factory { DeleteUseCase(get()) }
    factory { BuildBreadcrumbUseCase(get()) }
    factory { HashCalcUseCase(get()) }
}
