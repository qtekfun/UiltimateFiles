package com.qtekfun.ultimatefiles.domain.connection

import com.qtekfun.ultimatefiles.core.model.ConnectionProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Which server accounts failed the last time they were used. It is only updated when the user does something with an
 * account (opens a folder, refreshes, copies): nothing pings servers in the background.
 */
class ConnectionHealth {
    private val _problems = MutableStateFlow<Map<String, ConnectionProblem>>(emptyMap())

    /** The last problem of every account that is currently unreachable, by account id. */
    val problems: StateFlow<Map<String, ConnectionProblem>> = _problems.asStateFlow()

    /** Remembers [problem] for its account; a problem without an account cannot be attributed and is ignored. */
    fun report(problem: ConnectionProblem) {
        val id = problem.accountId ?: return
        _problems.update { it + (id to problem) }
    }

    /** A later use of the account worked (or it was edited or removed): it is no longer marked. */
    fun clear(accountId: String) {
        _problems.update { if (accountId in it) it - accountId else it }
    }

    fun clear(accountIds: Collection<String>) = accountIds.forEach(::clear)
}
