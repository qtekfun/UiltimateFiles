package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind
import net.schmizz.sshj.userauth.UserAuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

class ConnectionProblemsTest {
    private fun kind(error: Throwable) = ConnectionProblems.classify(error)

    @Test
    fun `an unknown host or no route means there is no network`() {
        assertEquals(ConnectionProblemKind.OFFLINE, kind(UnknownHostException("cloud.example.com")))
        assertEquals(ConnectionProblemKind.OFFLINE, kind(NoRouteToHostException("No route to host")))
        assertEquals(ConnectionProblemKind.OFFLINE, kind(SocketException("Network is unreachable")))
        assertEquals(ConnectionProblemKind.OFFLINE, kind(IOException("Unable to resolve host \"nas.local\": No address associated")))
    }

    @Test
    fun `timeouts, refusals and dropped connections mean the server does not answer`() {
        assertEquals(ConnectionProblemKind.NO_RESPONSE, kind(SocketTimeoutException("timeout")))
        assertEquals(ConnectionProblemKind.NO_RESPONSE, kind(ConnectException("Connection refused")))
        assertEquals(ConnectionProblemKind.NO_RESPONSE, kind(SocketException("Connection reset")))
        assertEquals(ConnectionProblemKind.NO_RESPONSE, kind(EOFException()))
        assertEquals(ConnectionProblemKind.NO_RESPONSE, kind(WebDavException(503, "Service Unavailable")))
        assertEquals(ConnectionProblemKind.NO_RESPONSE, kind(SSLException("Connection reset by peer")))
    }

    @Test
    fun `a rejected sign-in is an authentication problem on every protocol`() {
        assertEquals(ConnectionProblemKind.AUTH, kind(WebDavException(401, "Unauthorized")))
        assertEquals(ConnectionProblemKind.AUTH, kind(WebDavException(403, "Forbidden")))
        assertEquals(ConnectionProblemKind.AUTH, kind(UserAuthException("Exhausted available authentication methods")))
        assertEquals(ConnectionProblemKind.AUTH, kind(IOException("STATUS_LOGON_FAILURE (0xc000006d)")))
    }

    @Test
    fun `a changed certificate or host key is an identity problem`() {
        assertEquals(ConnectionProblemKind.IDENTITY, kind(HostKeyChangedException("SHA256:old", "SHA256:new")))
        assertEquals(ConnectionProblemKind.IDENTITY, kind(SSLHandshakeException("PKIX path building failed")))
        assertEquals(ConnectionProblemKind.IDENTITY, kind(SSLException("certificate is not the one that was trusted")))
    }

    @Test
    fun `a missing address or share is not found`() {
        assertEquals(ConnectionProblemKind.NOT_FOUND, kind(WebDavException(404, "Not Found")))
        assertEquals(ConnectionProblemKind.NOT_FOUND, kind(IOException("STATUS_BAD_NETWORK_NAME")))
    }

    @Test
    fun `the cause chain is read, and the most telling cause wins`() {
        assertEquals(ConnectionProblemKind.OFFLINE, kind(IOException("Could not list", IOException("lower", UnknownHostException("nas")))))
        // A timeout whose cause is an untrusted certificate is still about identity.
        assertEquals(
            ConnectionProblemKind.IDENTITY,
            kind(SocketTimeoutException("slow").also { it.initCause(SSLHandshakeException("bad certificate")) }),
        )
        assertEquals(
            ConnectionProblemKind.AUTH,
            kind(ConnectException("refused").also { it.initCause(WebDavException(401, "no")) }),
        )
    }

    @Test
    fun `errors that are not about the connection are not classified`() {
        assertNull(kind(IOException("Already exists: report.txt")))
        assertNull(kind(IllegalArgumentException("Not a valid host")))
        assertNull(kind(WebDavException(409, "Conflict")))
        assertNull(kind(RuntimeException("boom")))
    }

    @Test
    fun `a cycle in the causes does not loop forever`() {
        val a = IOException("a")
        val b = IOException("b", a)
        a.initCause(b)
        assertNull(kind(a))
    }
}
