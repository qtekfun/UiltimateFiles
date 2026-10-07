package com.qtekfun.ultimatefiles.core.model

enum class StorageKind { INTERNAL, USB_OTG, SD_CARD, NETWORK }

/** A browsable storage root. [rootPath] is the [FileItem.path] of the volume's top directory. */
data class StorageVolume(
    val id: String,
    val label: String,
    val rootPath: String,
    val kind: StorageKind,
    val isEjectable: Boolean,
    /** Size and free space of the volume, when the backend can tell (local and removable storage). */
    val totalBytes: Long? = null,
    val freeBytes: Long? = null,
)
