package com.qtekfun.ultimatefiles.data.network

import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.IOException
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/** The server's host key is not one the user has trusted yet; [fingerprint] is what they are asked to confirm. */
class UntrustedHostKeyException(val fingerprint: String, val keyType: String) :
    IOException("Unknown host key $keyType $fingerprint")

/** The key the server presented is not the one pinned for this account: either a new server or someone in between. */
class HostKeyChangedException(val expected: String, val actual: String) :
    IOException("The host key changed (expected $expected, got $actual)")

/** Opens authenticated SSH connections. [newClient] is the only place that knows which sshj configuration to use. */
class SshConnector(private val newClient: () -> SSHClient = { SSHClient(AndroidConfig()) }) {

    /**
     * Connects to [host]:[port] and logs in with a password. With [pinnedFingerprint] only that host key is accepted;
     * without it the connection is refused with [UntrustedHostKeyException] so the caller can ask the user.
     */
    fun connect(host: String, port: Int, username: String, password: String, pinnedFingerprint: String?): SSHClient {
        var seen: Pair<String, String>? = null
        val ssh = newClient()
        ssh.connectTimeout = CONNECT_TIMEOUT_MILLIS
        ssh.timeout = READ_TIMEOUT_MILLIS
        ssh.addHostKeyVerifier(
            object : HostKeyVerifier {
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    val fingerprint = fingerprintOf(key)
                    seen = fingerprint to key.algorithm
                    return pinnedFingerprint != null && fingerprint == pinnedFingerprint
                }

                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
            },
        )
        try {
            ssh.connect(host, port)
            ssh.authPassword(username, password)
            return ssh
        } catch (e: IOException) {
            runCatching { ssh.close() }
            val (fingerprint, type) = seen ?: throw e
            if (pinnedFingerprint == null) throw UntrustedHostKeyException(fingerprint, type)
            if (fingerprint != pinnedFingerprint) throw HostKeyChangedException(pinnedFingerprint, fingerprint)
            throw e
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 15_000
        private const val READ_TIMEOUT_MILLIS = 30_000

        /** Same notation as `ssh-keygen -lf`: `SHA256:` and the unpadded Base64 of the digest of the public key blob. */
        fun fingerprintOf(key: PublicKey): String {
            val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
            val digest = MessageDigest.getInstance("SHA-256").digest(blob)
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
        }

        /** A connector for the JVM tests, where Android's restricted configuration is not needed. */
        fun forJvm() = SshConnector { SSHClient(DefaultConfig()) }
    }
}
