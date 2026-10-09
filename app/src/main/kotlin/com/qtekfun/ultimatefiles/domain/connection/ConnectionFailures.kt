package com.qtekfun.ultimatefiles.domain.connection

import com.qtekfun.ultimatefiles.core.model.ConnectionProblem
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import com.qtekfun.ultimatefiles.core.util.RemoteAccounts

object ConnectionFailures {
    /**
     * The connection problem to attach to a transfer that failed with [error], or null when no server was involved in
     * [request] or the error is not a connection problem. [touched] are the paths being worked on when it failed: if
     * exactly one account is among them it is the one to blame, otherwise the request's only account, otherwise none.
     */
    fun forRequest(
        classifier: ConnectionFailureClassifier,
        error: Throwable,
        request: TransferRequest,
        touched: List<String> = emptyList(),
    ): ConnectionProblem? {
        val involved = RemoteAccounts.idsIn(request.items, request.targetDirectory)
        if (involved.isEmpty()) return null
        val kind = classifier.classify(error) ?: return null
        val account = RemoteAccounts.idsIn(touched).singleOrNull() ?: involved.singleOrNull()
        return ConnectionProblem(kind, account)
    }
}
