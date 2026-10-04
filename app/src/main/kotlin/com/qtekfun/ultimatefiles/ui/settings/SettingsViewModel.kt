package com.qtekfun.ultimatefiles.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatefiles.core.model.PanelBarPosition
import com.qtekfun.ultimatefiles.core.model.ThemeMode
import com.qtekfun.ultimatefiles.core.model.UserPreferences
import com.qtekfun.ultimatefiles.data.backup.BackupException
import com.qtekfun.ultimatefiles.data.backup.BackupFiles
import com.qtekfun.ultimatefiles.data.backup.BackupManager
import com.qtekfun.ultimatefiles.data.backup.BackupProblem
import com.qtekfun.ultimatefiles.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Outcome of an export or import, shown once by the settings screen. */
sealed interface BackupOutcome {
    data object Exported : BackupOutcome
    data class Imported(val settings: Boolean, val accounts: Int) : BackupOutcome
    /** The file holds accounts: ask for the passphrase it was exported with and retry [uri]. */
    data class NeedsPassphrase(val uri: Uri, val wrong: Boolean) : BackupOutcome
    data object InvalidFile : BackupOutcome
    data object UnsupportedVersion : BackupOutcome
    data class Failed(val reason: String?) : BackupOutcome
}

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val backup: BackupManager,
    private val backupFiles: BackupFiles,
) : ViewModel() {

    /** Null until the stored preferences have been read, so the UI does not flash the default theme. */
    val preferences: StateFlow<UserPreferences?> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val accountCount: StateFlow<Int> = backup.accountCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _outcome = MutableStateFlow<BackupOutcome?>(null)
    val outcome: StateFlow<BackupOutcome?> = _outcome.asStateFlow()

    fun dismissOutcome() {
        _outcome.value = null
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setPanelBarPosition(position: PanelBarPosition) {
        viewModelScope.launch { preferencesRepository.setPanelBarPosition(position) }
    }

    fun setVerifyCopies(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVerifyCopies(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
    }

    /** Writes the backup to [uri]; [passphrase] protects the accounts' app passwords when they are included. */
    fun exportBackup(uri: Uri, includeAccounts: Boolean, passphrase: String?) {
        viewModelScope.launch {
            _outcome.value = try {
                backupFiles.write(uri, backup.export(includeAccounts, passphrase))
                BackupOutcome.Exported
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is BackupException) BackupOutcome.Failed(e.problem.name) else failure(e, uri)
            }
        }
    }

    fun importBackup(uri: Uri, passphrase: String?) {
        viewModelScope.launch {
            _outcome.value = try {
                val summary = backup.import(backupFiles.read(uri), passphrase)
                BackupOutcome.Imported(summary.settingsApplied, summary.accountsAdded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure(e, uri)
            }
        }
    }

    private fun failure(e: Exception, uri: Uri): BackupOutcome =
        if (e is BackupException) {
            when (e.problem) {
                BackupProblem.INVALID_FILE -> BackupOutcome.InvalidFile
                BackupProblem.UNSUPPORTED_VERSION -> BackupOutcome.UnsupportedVersion
                BackupProblem.NEEDS_PASSPHRASE -> BackupOutcome.NeedsPassphrase(uri, wrong = false)
                BackupProblem.WRONG_PASSPHRASE -> BackupOutcome.NeedsPassphrase(uri, wrong = true)
            }
        } else {
            BackupOutcome.Failed(e.message)
        }
}
