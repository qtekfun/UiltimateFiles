package com.qtekfun.ultimatefiles.core.model

/** Why a server could not be used, in terms the user can act on. */
enum class ConnectionProblemKind {
    /** No route to the server: the device has no network, or the name does not resolve. */
    OFFLINE,

    /** The server did not answer in time, refused the connection or dropped it. */
    NO_RESPONSE,

    /** The server rejected the user name, password or key. */
    AUTH,

    /** The certificate or SSH host key is not the one that was trusted. */
    IDENTITY,

    /** The account's address or share no longer exists on the server. */
    NOT_FOUND,

    /** A failure while using a server that none of the above explains. */
    OTHER,
}

/** A connection problem of one account; [accountId] is null when it cannot be told which server was meant. */
data class ConnectionProblem(
    val kind: ConnectionProblemKind,
    val accountId: String? = null,
    /** Technical detail shown only for [ConnectionProblemKind.OTHER]. */
    val detail: String? = null,
)

/**
 * How a connection problem travels through the history log, whose entries only carry an error string: a stable prefix, the
 * kind and the account name at that time (names change, so the id is not enough). Older entries hold plain text and
 * are not touched by [decode].
 */
object ConnectionProblemCodec {
    private const val PREFIX = "connection:"

    fun encode(kind: ConnectionProblemKind, accountLabel: String?): String = "$PREFIX${kind.name}:${accountLabel.orEmpty()}"

    /** The kind and the account name (null when there was none), or null when [error] is not an encoded problem. */
    fun decode(error: String?): Pair<ConnectionProblemKind, String?>? {
        if (error == null || !error.startsWith(PREFIX)) return null
        val parts = error.removePrefix(PREFIX).split(':', limit = 2)
        val kind = ConnectionProblemKind.entries.firstOrNull { it.name == parts[0] } ?: return null
        return kind to parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
    }
}
