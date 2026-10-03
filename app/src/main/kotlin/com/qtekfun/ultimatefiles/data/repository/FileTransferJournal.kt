package com.qtekfun.ultimatefiles.data.repository

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import com.qtekfun.ultimatefiles.domain.transfer.TransferJournal
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** [TransferJournal] as one small JSON file, replaced atomically on every change. */
class FileTransferJournal(private val file: File) : TransferJournal {
    private val lock = Any()

    override fun load(): List<TransferRequest> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { toRequest(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            emptyList() // a damaged journal is not worth failing the app over
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
    }

    override fun save(requests: List<TransferRequest>) = synchronized(lock) {
        if (requests.isEmpty()) {
            file.delete()
            return@synchronized
        }
        val array = JSONArray()
        requests.forEach { array.put(toJson(it)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("Cannot save the transfer journal")
        }
    }

    private fun toJson(request: TransferRequest): JSONObject = JSONObject()
        .put("operation", request.operation.name)
        .put("target", request.targetDirectory)
        .put("verify", request.verify)
        .put("archiveName", request.archiveName ?: JSONObject.NULL)
        .put(
            "items",
            JSONArray().also { items ->
                request.items.forEach { item ->
                    items.put(
                        JSONObject()
                            .put("path", item.path)
                            .put("name", item.name)
                            .put("dir", item.isDirectory)
                            .put("size", item.sizeBytes)
                            .put("modified", item.lastModifiedMillis)
                            .put("mime", item.mimeType ?: JSONObject.NULL)
                            .put("writable", item.isWritable)
                            .put("hidden", item.isHidden),
                    )
                }
            },
        )

    private fun toRequest(json: JSONObject): TransferRequest {
        val items = json.getJSONArray("items")
        return TransferRequest(
            operation = OperationType.valueOf(json.getString("operation")),
            items = (0 until items.length()).map { index ->
                val row = items.getJSONObject(index)
                FileItem(
                    path = row.getString("path"),
                    name = row.getString("name"),
                    isDirectory = row.getBoolean("dir"),
                    sizeBytes = row.getLong("size"),
                    lastModifiedMillis = row.getLong("modified"),
                    mimeType = if (row.isNull("mime")) null else row.getString("mime"),
                    isWritable = row.optBoolean("writable", true),
                    isHidden = row.optBoolean("hidden", false),
                )
            },
            targetDirectory = json.getString("target"),
            verify = json.optBoolean("verify", false),
            archiveName = if (json.isNull("archiveName")) null else json.optString("archiveName"),
        )
    }
}
