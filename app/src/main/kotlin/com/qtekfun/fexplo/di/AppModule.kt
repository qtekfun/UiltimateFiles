package com.qtekfun.fexplo.di

import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.qtekfun.fexplo.R
import com.qtekfun.fexplo.core.datastore.DataStoreUserPreferencesRepository
import com.qtekfun.fexplo.core.model.PanelId
import com.qtekfun.fexplo.data.backup.BackupFiles
import com.qtekfun.fexplo.data.backup.BackupManager
import com.qtekfun.fexplo.data.io.FileStreamCopier
import com.qtekfun.fexplo.data.network.FileUploadResumeStore
import com.qtekfun.fexplo.data.network.UploadResumeStore
import com.qtekfun.fexplo.data.network.WebDavAccountService
import com.qtekfun.fexplo.data.network.WebDavClient
import com.qtekfun.fexplo.data.repository.DataStoreAccountRepository
import com.qtekfun.fexplo.data.repository.FileTransferHistoryRepository
import com.qtekfun.fexplo.data.repository.LocalFileSystemRepository
import com.qtekfun.fexplo.data.repository.RoutingFileSystemRepository
import com.qtekfun.fexplo.data.repository.SafFileSystemRepository
import com.qtekfun.fexplo.data.repository.WebDavFileSystemRepository
import com.qtekfun.fexplo.data.service.ServiceTransferLauncher
import com.qtekfun.fexplo.data.service.TransferNotifications
import com.qtekfun.fexplo.data.system.AndroidKeystoreCipher
import com.qtekfun.fexplo.data.system.CompositeVolumeChangeSource
import com.qtekfun.fexplo.data.system.IntentFactory
import com.qtekfun.fexplo.data.system.SystemVolumeMonitor
import com.qtekfun.fexplo.domain.clipboard.ClipboardManager
import com.qtekfun.fexplo.domain.repository.AccountRepository
import com.qtekfun.fexplo.domain.repository.FileSystemRepository
import com.qtekfun.fexplo.domain.repository.SecretCipher
import com.qtekfun.fexplo.domain.history.TransferHistoryRecorder
import com.qtekfun.fexplo.domain.history.TransferHistoryRepository
import com.qtekfun.fexplo.domain.repository.UserPreferencesRepository
import com.qtekfun.fexplo.domain.repository.VolumeChangeSource
import com.qtekfun.fexplo.domain.transfer.TransferCoordinator
import com.qtekfun.fexplo.domain.transfer.TransferServiceLauncher
import com.qtekfun.fexplo.domain.usecase.BatchCopyUseCase
import com.qtekfun.fexplo.domain.usecase.BatchMoveUseCase
import com.qtekfun.fexplo.domain.usecase.BuildBreadcrumbUseCase
import com.qtekfun.fexplo.domain.usecase.DeleteUseCase
import com.qtekfun.fexplo.domain.usecase.HashCalcUseCase
import com.qtekfun.fexplo.domain.usecase.StreamCopier
import com.qtekfun.fexplo.domain.usecase.TransferEngine
import com.qtekfun.fexplo.ui.browser.BrowserViewModel
import com.qtekfun.fexplo.ui.dualpanel.DragDropState
import com.qtekfun.fexplo.ui.history.HistoryViewModel
import com.qtekfun.fexplo.ui.main.MainViewModel
import com.qtekfun.fexplo.ui.settings.SettingsViewModel
import java.io.File
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
    single<SecretCipher> { AndroidKeystoreCipher() }
    single<AccountRepository> { DataStoreAccountRepository(get(), get()) }
    single { WebDavClient() }
    single<UploadResumeStore> { FileUploadResumeStore(File(androidContext().filesDir, "upload-resume.tsv")) }
    single { WebDavFileSystemRepository(accounts = get(), client = get(), resumeStore = get()) }
    single { WebDavAccountService(accounts = get(), client = get()) }
    single<FileSystemRepository> {
        RoutingFileSystemRepository(
            local = get<LocalFileSystemRepository>(),
            saf = get<SafFileSystemRepository>(),
            webDav = get<WebDavFileSystemRepository>(),
        )
    }

    single { TransferEngine(repository = get(), copier = get()) }
    single<TransferServiceLauncher> { ServiceTransferLauncher(androidContext()) }
    single<TransferHistoryRepository> { FileTransferHistoryRepository(File(androidContext().filesDir, "history.tsv")) }
    single { TransferHistoryRecorder(history = get(), files = get()) }
    single<VolumeChangeSource> { CompositeVolumeChangeSource(SystemVolumeMonitor(androidContext()), get()) }
    single { TransferCoordinator(engine = get(), recorder = get(), launcher = get()) }
    single { TransferNotifications(androidContext()) }

    factory { BatchCopyUseCase(get(), get()) }
    factory { BatchMoveUseCase(get(), get()) }
    factory { DeleteUseCase(get(), get()) }
    factory { BuildBreadcrumbUseCase(get()) }
    factory { HashCalcUseCase(get()) }

    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("settings") }
    }
    single<UserPreferencesRepository> { DataStoreUserPreferencesRepository(get()) }
    single { IntentFactory(androidContext()) }
    single { DragDropState() }

    // ViewModels are created through ViewModelProvider factories in the UI; Koin only supplies the dependencies.
    factory { MainViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { BackupManager(preferences = get(), accounts = get()) }
    single { BackupFiles(androidContext()) }
    factory { SettingsViewModel(get(), get(), get()) }
    factory { HistoryViewModel(get(), get()) }
    factory { params ->
        BrowserViewModel(
            panel = params.get<PanelId>(),
            repository = get(),
            preferences = get(),
            clipboardManager = get(),
            coordinator = get(),
            buildBreadcrumb = get(),
            deleteFiles = get(),
            calculateHash = get(),
            copyFiles = get(),
            moveFiles = get(),
            dragDrop = get(),
            volumeChanges = get(),
        )
    }
}
