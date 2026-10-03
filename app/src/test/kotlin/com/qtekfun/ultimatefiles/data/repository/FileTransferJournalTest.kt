package com.qtekfun.ultimatefiles.data.repository

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileTransferJournalTest {
    @get:Rule val tmp = TemporaryFolder()

    private val request = TransferRequest(
        operation = OperationType.CUT,
        items = listOf(
            FileItem("/storage/DCIM/clip \"1\".mp4", "clip \"1\".mp4", false, 8_589_934_592L, 1_700_000_000_000L, "video/mp4"),
            FileItem("content://tree/folder\ttab", "folder\ttab", true, 0L, 0L, null, isWritable = false, isHidden = true),
        ),
        targetDirectory = "dav://abc/Footage",
        verify = true,
    )

    @Test fun `requests survive a round trip with awkward names and big sizes`() {
        val journal = FileTransferJournal(File(tmp.root, "journal.json"))
        journal.save(listOf(request, request.copy(operation = OperationType.COPY, verify = false)))

        val loaded = FileTransferJournal(File(tmp.root, "journal.json")).load() // a fresh instance, like after a restart

        assertEquals(2, loaded.size)
        assertEquals(request, loaded[0])
        assertEquals(OperationType.COPY, loaded[1].operation)
        assertFalse(loaded[1].verify)
    }

    @Test fun `saving nothing removes the file and a missing file loads as empty`() {
        val file = File(tmp.root, "journal.json")
        val journal = FileTransferJournal(file)
        assertTrue(journal.load().isEmpty())
        journal.save(listOf(request))
        assertTrue(file.exists())
        journal.save(emptyList())
        assertFalse(file.exists())
    }

    @Test fun `a damaged journal is ignored instead of crashing the app`() {
        val file = File(tmp.root, "journal.json").apply { writeText("{not json") }
        assertTrue(FileTransferJournal(file).load().isEmpty())
    }
}
