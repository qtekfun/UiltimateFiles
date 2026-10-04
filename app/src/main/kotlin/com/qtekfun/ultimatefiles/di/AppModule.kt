package com.qtekfun.ultimatefiles.di

import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.datastore.DataStoreUserPreferencesRepository
import com.qtekfun.ultimatefiles.core.model.PanelId
import com.qtekfun.ultimatefiles.data.backup.BackupFiles
import com.qtekfun.ultimatefiles.data.backup.BackupManager
import com.qtekfun.ultimatefiles.data.io.FileStreamCopier
import com.qtekfun.ultimatefiles.data.network.FileUploadResumeStore
import com.qtekfun.ultimatefiles.data.network.UploadResumeStore
import com.qtekfun.ultimatefiles.data.network.SftpAccountService
import com.qtekfun.ultimatefiles.data.network.SmbAccountService
import com.qtekfun.ultimatefiles.data.network.SmbConnector
import com.qtekfun.ultimatefiles.data.system.IncomingFiles
import com.qtekfun.ultimatefiles.data.network.SshConnector
import com.qtekfun.ultimatefiles.data.network.WebDavAccountService
import com.qtekfun.ultimatefiles.data.network.WebDavClient
import com.qtekfun.ultimatefiles.data.repository.ArchiveFileSystemRepository
import com.qtekfun.ultimatefiles.data.repository.DataStoreAccountRepository
import com.qtekfun.ultimatefiles.data.repository.FileTransferHistoryRepository
import com.qtekfun.ultimatefiles.data.repository.LocalFileSystemRepository
import com.qtekfun.ultimatefiles.data.repository.RoutingFileSystemRepository
import com.qtekfun.ultimatefiles.data.repository.SafFileSystemRepository
import com.qtekfun.ultimatefiles.data.repository.SftpFileSystemRepository
import com.qtekfun.ultimatefiles.data.repository.SmbFileSystemRepository
import com.qtekfun.ultimatefiles.data.repository.WebDavFileSystemRepository
import com.qtekfun.ultimatefiles.data.service.ServiceTransferLauncher
import com.qtekfun.ultimatefiles.data.service.TransferNotifications
import com.qtekfun.ultimatefiles.data.system.AndroidKeystoreCipher
import com.qtekfun.ultimatefiles.data.system.CompositeVolumeChangeSource
import com.qtekfun.ultimatefiles.data.system.IntentFactory
import com.qtekfun.ultimatefiles.data.system.SystemVolumeMonitor
import com.qtekfun.ultimatefiles.domain.clipboard.ClipboardManager
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import com.qtekfun.ultimatefiles.domain.repository.SecretCipher
import com.qtekfun.ultimatefiles.domain.history.TransferHistoryRecorder
import com.qtekfun.ultimatefiles.domain.history.TransferHistoryRepository
import com.qtekfun.ultimatefiles.domain.repository.UserPreferencesRepository
import com.qtekfun.ultimatefiles.domain.repository.VolumeChangeSource
import com.qtekfun.ultimatefiles.data.repository.FileTransferJournal
import com.qtekfun.ultimatefiles.domain.transfer.TransferCoordinator
import com.qtekfun.ultimatefiles.domain.transfer.TransferJournal
import com.qtekfun.ultimatefiles.domain.transfer.TransferServiceLauncher
import com.qtekfun.ultimatefiles.domain.usecase.BatchCopyUseCase
import com.qtekfun.ultimatefiles.domain.usecase.BatchMoveUseCase
import com.qtekfun.ultimatefiles.domain.usecase.BuildBreadcrumbUseCase
import com.qtekfun.ultimatefiles.domain.usecase.DeleteUseCase
import com.qtekfun.ultimatefiles.domain.usecase.HashCalcUseCase
import com.qtekfun.ultimatefiles.domain.usecase.StreamCopier
import com.qtekfun.ultimatefiles.domain.usecase.TransferEngine
import com.qtekfun.ultimatefiles.ui.browser.BrowserViewModel
import com.qtekfun.ultimatefiles.ui.dualpanel.DragDropState
import com.qtekfun.ultimatefiles.ui.history.HistoryViewModel
import com.qtekfun.ultimatefiles.ui.main.MainViewModel
import com.qtekfun.ultimatefiles.ui.settings.SettingsViewModel
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
    single { SshConnector() }
    single { IncomingFiles(androidContext()) }
    single { SftpFileSystemRepository(accounts = get(), connector = get()) }
    single { SftpAccountService(accounts = get(), connector = get()) }
    single { SmbConnector() }
    single { SmbFileSystemRepository(accounts = get(), connector = get()) }
    single { SmbAccountService(accounts = get(), connector = get()) }
    single {
        val scope = this
        ArchiveFileSystemRepository(source = { scope.get<FileSystemRepository>() }, cacheDir = File(androidContext().cacheDir, "archives"))
    }
    single<FileSystemRepository> {
        RoutingFileSystemRepository(
            local = get<LocalFileSystemRepository>(),
            saf = get<SafFileSystemRepository>(),
            webDav = get<WebDavFileSystemRepository>(),
            sftp = get<SftpFileSystemRepository>(),
            archive = get<ArchiveFileSystemRepository>(),
            smb = get<SmbFileSystemRepository>(),
        )
    }

    single { TransferEngine(repository = get(), copier = get()) }
    single<TransferServiceLauncher> { ServiceTransferLauncher(androidContext()) }
    single<TransferHistoryRepository> { FileTransferHistoryRepository(File(androidContext().filesDir, "history.tsv")) }
    single { TransferHistoryRecorder(history = get(), files = get()) }
    single<VolumeChangeSource> { CompositeVolumeChangeSource(SystemVolumeMonitor(androidContext()), get()) }
    single<TransferJournal> { FileTransferJournal(File(androidContext().filesDir, "transfer-journal.json")) }
    single { TransferCoordinator(engine = get(), recorder = get(), journal = get(), launcher = get()) }
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
    factory { MainViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
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
