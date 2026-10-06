package com.qtekfun.ultimatefiles.data.system

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import com.qtekfun.ultimatefiles.core.model.StorageKind
import com.qtekfun.ultimatefiles.core.model.StorageVolume
import android.os.storage.StorageVolume as SystemVolume

/**
 * The removable volumes (USB drives, SD cards) Android has mounted, as folders the app can read and write by path.
 *
 * Going through the path is faster than the Storage Access Framework and keeps names as they are, which a documents
 * provider does not. It needs the all-files access the app already asks for, and that only reaches secondary
 * volumes from Android 11; before that, only a folder granted through the system picker can be written.
 */
class RemovableStorage(private val context: Context) {

    fun volumes(): List<StorageVolume> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val manager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return manager.storageVolumes
            .filter { it.isRemovable && !it.isPrimary && it.isReachable() }
            .mapNotNull { volume ->
                val directory = volume.directory ?: return@mapNotNull null
                val label = volume.getDescription(context) ?: directory.name
                StorageVolume(
                    id = ID_PREFIX + (volume.uuid ?: directory.name),
                    label = label,
                    rootPath = directory.path,
                    kind = if (label.contains("SD", ignoreCase = true)) StorageKind.SD_CARD else StorageKind.USB_OTG,
                    isEjectable = true,
                )
            }
    }

    private fun SystemVolume.isReachable() = state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY

    companion object {
        /** Tells these volumes apart from the ones granted through the picker, whose id is a URI. */
        const val ID_PREFIX = "removable:"

        /**
         * Whether a folder granted through the picker (its tree URI) is the volume [volumeId] already shown by path, so
         * the drawer does not list the same drive twice.
         */
        fun isSameVolume(treeUri: String, volumeId: String): Boolean {
            val uuid = volumeId.removePrefix(ID_PREFIX)
            return treeUri.contains("/tree/$uuid%3A") || treeUri.contains("/tree/$uuid:")
        }
    }
}
