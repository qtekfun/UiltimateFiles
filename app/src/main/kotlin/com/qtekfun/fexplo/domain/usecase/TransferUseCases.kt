package com.qtekfun.fexplo.domain.usecase

import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.OperationType
import com.qtekfun.fexplo.core.model.TransferRequest
import com.qtekfun.fexplo.domain.repository.FileSystemRepository
import com.qtekfun.fexplo.domain.transfer.TransferCoordinator

/** Queues a background copy of [items] into [targetDirectory]. */
class BatchCopyUseCase(private val coordinator: TransferCoordinator) {
    operator fun invoke(items: List<FileItem>, targetDirectory: String) {
        coordinator.enqueue(TransferRequest(OperationType.COPY, items, targetDirectory))
    }
}

/** Queues a background move (copy + delete source) of [items] into [targetDirectory]. */
class BatchMoveUseCase(private val coordinator: TransferCoordinator) {
    operator fun invoke(items: List<FileItem>, targetDirectory: String) {
        coordinator.enqueue(TransferRequest(OperationType.CUT, items, targetDirectory))
    }
}

class DeleteUseCase(private val repository: FileSystemRepository) {
    suspend operator fun invoke(items: List<FileItem>): Result<Unit> = repository.delete(items)
}
