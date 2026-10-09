package com.qtekfun.ultimatefiles.domain.connection

import com.qtekfun.ultimatefiles.core.model.ConnectionProblem
import com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class ConnectionHealthTest {
    @Test
    fun `a problem is remembered for its account until it is cleared`() {
        val health = ConnectionHealth()
        health.report(ConnectionProblem(ConnectionProblemKind.NO_RESPONSE, "nas"))
        health.report(ConnectionProblem(ConnectionProblemKind.AUTH, "cloud"))
        assertEquals(setOf("nas", "cloud"), health.problems.value.keys)

        health.clear("nas")

        assertEquals(setOf("cloud"), health.problems.value.keys)
        health.clear(listOf("cloud", "unknown"))
        assertTrue(health.problems.value.isEmpty())
    }

    @Test
    fun `a newer problem replaces the older one, and one without an account is ignored`() {
        val health = ConnectionHealth()
        health.report(ConnectionProblem(ConnectionProblemKind.NO_RESPONSE, "nas"))
        health.report(ConnectionProblem(ConnectionProblemKind.AUTH, "nas"))
        health.report(ConnectionProblem(ConnectionProblemKind.OFFLINE, null))

        assertEquals(ConnectionProblemKind.AUTH, health.problems.value["nas"]?.kind)
        assertEquals(1, health.problems.value.size)
    }

    private fun file(path: String) = FileItem(path, path.substringAfterLast('/'), false, 10, 0L, "text/plain")
    private val timeout = SocketTimeoutException("timeout")
    private val classifier = ConnectionFailureClassifier { com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind.NO_RESPONSE }

    @Test
    fun `a failed batch blames the only server it touches, or the one being worked on`() {
        val one = TransferRequest(OperationType.COPY, listOf(file("dav://nas/a.txt")), "/storage/emulated/0/Download")
        assertEquals("nas", ConnectionFailures.forRequest(classifier, timeout, one)?.accountId)

        val two = TransferRequest(OperationType.COPY, listOf(file("dav://nas/a.txt")), "sftp://pi/backup")
        assertNull("two servers and nothing to tell them apart", ConnectionFailures.forRequest(classifier, timeout, two)?.accountId)
        assertNull("both servers are being worked on", ConnectionFailures.forRequest(classifier, timeout, two, touched = listOf("dav://nas/a.txt", "sftp://pi/backup"))?.accountId)
        assertEquals("nas", ConnectionFailures.forRequest(classifier, timeout, two, touched = listOf("dav://nas/a.txt", "/local"))?.accountId)
    }

    @Test
    fun `a batch that only touches this device never reports a connection problem`() {
        val local = TransferRequest(OperationType.COPY, listOf(file("/storage/emulated/0/a.txt")), "/storage/emulated/0/Download")
        assertNull(ConnectionFailures.forRequest(classifier, timeout, local))
        assertNull(ConnectionFailures.forRequest({ null }, timeout, TransferRequest(OperationType.COPY, listOf(file("dav://nas/a")), "/x")))
    }
}
