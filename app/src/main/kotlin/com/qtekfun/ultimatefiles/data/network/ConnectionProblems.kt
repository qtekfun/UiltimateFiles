package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind
import com.qtekfun.ultimatefiles.domain.connection.ConnectionFailureClassifier
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Reads the whole cause chain of an exception from a WebDAV, SFTP or SMB call and says why the server could not be used.
 * Nothing here needs the device's network state: the exception already says what happened.
 */
object ConnectionProblems : ConnectionFailureClassifier {
    /** When several causes are present, the first of these wins: an untrusted identity says more than a timeout. */
    private val PRECEDENCE = listOf(
        ConnectionProblemKind.IDENTITY,
        ConnectionProblemKind.AUTH,
        ConnectionProblemKind.NOT_FOUND,
        ConnectionProblemKind.OFFLINE,
        ConnectionProblemKind.NO_RESPONSE,
    )

    private val SSH_DROPPED = setOf(
        "net.schmizz.sshj.transport.TransportException",
        "net.schmizz.sshj.connection.ConnectionException",
    )
    private const val SSH_AUTH = "net.schmizz.sshj.userauth.UserAuthException"
    private const val SMB_DROPPED = "com.hierynomus.protocol.transport.TransportException"

    override fun classify(error: Throwable): ConnectionProblemKind? {
        val found = causesOf(error).mapNotNull(::kindOf).toSet()
        return PRECEDENCE.firstOrNull { it in found }
    }

    private fun causesOf(error: Throwable): List<Throwable> {
        val chain = ArrayList<Throwable>()
        var current: Throwable? = error
        while (current != null && chain.size < MAX_CAUSES && chain.none { it === current }) {
            chain += current
            current = current.cause
        }
        return chain
    }

    private fun kindOf(e: Throwable): ConnectionProblemKind? {
        val message = e.message.orEmpty()
        val name = e.javaClass.name
        return when {
            e is HostKeyChangedException || e is UntrustedHostKeyException || e is UntrustedCertificateException ->
                ConnectionProblemKind.IDENTITY
            e is SSLHandshakeException || e is SSLPeerUnverifiedException || e is CertificateException ->
                ConnectionProblemKind.IDENTITY
            // A plain SSL error that is really a dropped connection is not an identity problem.
            e is SSLException -> if (looksDropped(message)) ConnectionProblemKind.NO_RESPONSE else ConnectionProblemKind.IDENTITY

            name == SSH_AUTH -> ConnectionProblemKind.AUTH
            "LOGON_FAILURE" in message -> ConnectionProblemKind.AUTH
            e is WebDavException && (e.code == 401 || e.code == 403) -> ConnectionProblemKind.AUTH

            "BAD_NETWORK_NAME" in message -> ConnectionProblemKind.NOT_FOUND
            e is WebDavException && e.code == 404 -> ConnectionProblemKind.NOT_FOUND

            e is UnknownHostException || e is NoRouteToHostException -> ConnectionProblemKind.OFFLINE
            e is SocketException && looksUnreachable(message) -> ConnectionProblemKind.OFFLINE
            "Unable to resolve host" in message || looksUnreachable(message) -> ConnectionProblemKind.OFFLINE

            e is WebDavException && e.code >= 500 -> ConnectionProblemKind.NO_RESPONSE
            e is SocketTimeoutException || e is InterruptedIOException || e is TimeoutException -> ConnectionProblemKind.NO_RESPONSE
            e is ConnectException || e is SocketException || e is EOFException -> ConnectionProblemKind.NO_RESPONSE
            name in SSH_DROPPED || name == SMB_DROPPED -> ConnectionProblemKind.NO_RESPONSE
            else -> null
        }
    }

    private fun looksUnreachable(message: String) = "Network is unreachable" in message || "ENETUNREACH" in message

    private fun looksDropped(message: String) =
        listOf("reset", "closed", "Broken pipe", "EOF").any { it.lowercase() in message.lowercase() }

    private const val MAX_CAUSES = 12
}
