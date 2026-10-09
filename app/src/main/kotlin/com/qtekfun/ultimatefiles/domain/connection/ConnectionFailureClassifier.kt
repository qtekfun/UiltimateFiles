package com.qtekfun.ultimatefiles.domain.connection

import com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind

/** Decides whether an exception means a server could not be used, and why. Implemented where the network libraries are. */
fun interface ConnectionFailureClassifier {
    /** The reason, or null when [error] is not a connection problem (a name clash, a bad argument...). */
    fun classify(error: Throwable): ConnectionProblemKind?

    companion object {
        /** Classifies nothing; for code and tests that do not care about connection problems. */
        val None = ConnectionFailureClassifier { null }
    }
}
