package com.qtekfun.fexplo.domain.usecase

import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.OperationType
import com.qtekfun.fexplo.core.model.TransferRequest
import com.qtekfun.fexplo.domain.history.TransferHistoryRecorder
import com.qtekfun.fexplo.domain.repository.FileSystemRepository
import com.qtekfun.fexplo.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import com.qtekfun.fexplo.domain.transfer.TransferCoordinator

/** Queues a background copy of [items] into [targetDirectory]. */
class BatchCopyUseCase(
    private val coordinator: TransferCoordinator,
    private val preferences: UserPreferencesRepository,
) {
    suspend operator fun invoke(items: List<FileItem>, targetDirectory: String) {
        val verify = preferences.preferences.first().verifyCopies
        coordinator.enqueue(TransferRequest(OperationType.COPY, items, targetDirectory, verify))
    }
}

/** Queues a background move (copy + delete source) of [items] into [targetDirectory]. */
class BatchMoveUseCase(
    private val coordinator: TransferCoordinator,
    private val preferences: UserPreferencesRepository,
) {
    suspend operator fun invoke(items: List<FileItem>, targetDirectory: String) {
        val verify = preferences.preferences.first().verifyCopies
        coordinator.enqueue(TransferRequest(OperationType.CUT, items, targetDirectory, verify))
    }
}

class DeleteUseCase(
    private val repository: FileSystemRepository,
    private val recorder: TransferHistoryRecorder,
) {
    suspend operator fun invoke(items: List<FileItem>): Result<Unit> =
        repository.delete(items).also { recorder.recordDelete(items, it) }
}
