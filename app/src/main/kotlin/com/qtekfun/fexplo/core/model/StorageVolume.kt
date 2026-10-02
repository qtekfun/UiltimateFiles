package com.qtekfun.fexplo.core.model

enum class StorageKind { INTERNAL, USB_OTG, SD_CARD }

/** A browsable storage root. [rootId] is a [FileItem.id] of the volume's top directory. */
data class StorageVolume(
    val id: String,
    val label: String,
    val rootId: String,
    val kind: StorageKind,
    val isEjectable: Boolean,
)
