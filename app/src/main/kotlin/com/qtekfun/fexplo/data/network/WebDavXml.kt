package com.qtekfun.fexplo.data.network

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/** One resource of a PROPFIND answer; [path] is relative to the account root (empty for the root itself). */
data class DavEntry(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val contentType: String?,
) {
    val name: String get() = path.substringAfterLast('/')
}

/** Reads a WebDAV `207 Multi-Status` body. */
internal object WebDavXml {
    private const val DAV = "DAV:"

    fun parse(body: InputStream, session: WebDavSession): List<DavEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // A server has no business sending a DTD; refuse it where the parser supports the switch.
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (ignored: Exception) {
            }
        }
        val document = factory.newDocumentBuilder().parse(body)
        val responses = document.getElementsByTagNameNS(DAV, "response")
        return (0 until responses.length).mapNotNull { index ->
            val response = responses.item(index) as Element
            val href = response.firstChild("href")?.textContent?.trim() ?: return@mapNotNull null
            val prop = successfulProp(response) ?: return@mapNotNull null
            DavEntry(
                path = session.relativeTo(href),
                isDirectory = prop.firstChild("resourcetype")?.firstChild("collection") != null,
                sizeBytes = prop.firstChild("getcontentlength")?.textContent?.trim()?.toLongOrNull() ?: 0L,
                lastModifiedMillis = prop.firstChild("getlastmodified")?.textContent?.let(::parseDate) ?: 0L,
                contentType = prop.firstChild("getcontenttype")?.textContent?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }

    /** The `prop` of the propstat whose status is 200 (servers also list unknown properties under 404). */
    private fun successfulProp(response: Element): Element? {
        val propstats = response.getElementsByTagNameNS(DAV, "propstat")
        for (i in 0 until propstats.length) {
            val propstat = propstats.item(i) as Element
            val status = propstat.firstChild("status")?.textContent.orEmpty()
            if (status.contains(" 200")) return propstat.firstChild("prop")
        }
        return null
    }

    private fun Element.firstChild(localName: String): Element? {
        var node: Node? = firstChild
        while (node != null) {
            if (node is Element && node.localName == localName && node.namespaceURI == DAV) return node
            node = node.nextSibling
        }
        return null
    }

    private fun parseDate(text: String): Long? = try {
        ZonedDateTime.parse(text.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
