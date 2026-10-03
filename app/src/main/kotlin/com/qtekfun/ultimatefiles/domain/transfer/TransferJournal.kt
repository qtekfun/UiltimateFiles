package com.qtekfun.ultimatefiles.domain.transfer

import com.qtekfun.ultimatefiles.core.model.TransferRequest

/**
 * Remembers the copies and moves that have not finished, so that when the system kills the app (battery managers do)
 * the work can be offered again instead of being silently lost.
 */
interface TransferJournal {
    fun load(): List<TransferRequest>

    fun save(requests: List<TransferRequest>)
}

/** For callers and tests that do not need persistence. */
object NoJournal : TransferJournal {
    override fun load(): List<TransferRequest> = emptyList()

    override fun save(requests: List<TransferRequest>) = Unit
}
