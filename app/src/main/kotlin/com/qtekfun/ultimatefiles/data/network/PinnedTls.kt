package com.qtekfun.ultimatefiles.data.network

import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** What the user decided about a server whose identity the system cannot vouch for. */
data class TrustChoice(
    /** Accept exactly the certificate with this SHA-256 (any hex notation), and nothing else, for this server. */
    val pinnedSha256: String? = null,
    /** Allow a plain `http://` address. */
    val allowInsecureHttp: Boolean = false,
)

/** What the user needs to see to decide whether to trust a certificate. */
data class CertificateInfo(
    val sha256: String,
    val subject: String,
    val issuer: String,
    val notAfterMillis: Long,
)

/** The server presented a certificate the system does not trust; the user may pin it. */
class UntrustedCertificateException(val info: CertificateInfo) : IOException("Untrusted certificate ${info.sha256}")

/** Trust for self-signed servers: the user pins one certificate by fingerprint instead of switching verification off. */
object PinnedTls {
    /** Lower-case hex without separators, so `AB:CD`, `ab cd` and `abcd` compare equal. */
    fun normalize(fingerprint: String): String = fingerprint.filter { it.isLetterOrDigit() }.lowercase()

    /** `AB:CD:EF…` for display. */
    fun display(fingerprint: String): String = normalize(fingerprint).chunked(2).joinToString(":").uppercase()

    fun sha256(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }

    /**
     * [base] with certificate verification replaced by "the leaf certificate must have this SHA-256". Returns [base]
     * unchanged without a pin. Host names are not checked because the pinned certificate already is the identity.
     */
    fun clientFor(base: OkHttpClient, pinnedSha256: String?): OkHttpClient {
        if (pinnedSha256.isNullOrBlank()) return base
        val manager = PinnedTrustManager(normalize(pinnedSha256))
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), SecureRandom()) }
        return base.newBuilder()
            .sslSocketFactory(context.socketFactory, manager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /**
     * Connects to [host]:[port] only to read the certificate it presents, so the user can be asked about it.
     * Nothing is sent over the connection and the result is never trusted automatically.
     */
    fun probe(host: String, port: Int, timeoutMillis: Int = 10_000): CertificateInfo {
        val capture = CapturingTrustManager()
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(capture), SecureRandom()) }
        Socket().use { raw ->
            raw.connect(InetSocketAddress(host, port), timeoutMillis)
            raw.soTimeout = timeoutMillis
            (context.socketFactory.createSocket(raw, host, port, true) as SSLSocket).use { it.startHandshake() }
        }
        val leaf = capture.chain?.firstOrNull() ?: throw IOException("The server presented no certificate")
        return CertificateInfo(
            sha256 = sha256(leaf),
            subject = leaf.subjectX500Principal.name,
            issuer = leaf.issuerX500Principal.name,
            notAfterMillis = leaf.notAfter.time,
        )
    }

    private class PinnedTrustManager(private val expected: String) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            throw CertificateException("Client certificates are not used")

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("The server presented no certificate")
            val actual = sha256(leaf)
            if (!MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())) {
                throw CertificateException("The server certificate is not the one you trusted (it is $actual)")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private class CapturingTrustManager : X509TrustManager {
        var chain: Array<out X509Certificate>? = null

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            this.chain = chain
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
