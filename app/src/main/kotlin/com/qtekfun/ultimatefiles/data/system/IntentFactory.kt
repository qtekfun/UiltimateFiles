package com.qtekfun.ultimatefiles.data.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.util.MimeTypes
import com.qtekfun.ultimatefiles.domain.usecase.ViewerKind
import com.qtekfun.ultimatefiles.ui.viewer.ViewerActivity
import java.io.File

/**
 * Builds the system intents (open with, share, eject). It lives in the data layer because turning a
 * local path into a shareable `content://` URI needs `java.io.File`, which the UI must not touch.
 */
class IntentFactory(private val context: Context) {

    fun view(item: FileItem, chooser: Boolean): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(item), item.mimeType ?: MimeTypes.fromName(item.name) ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return if (chooser) Intent.createChooser(intent, item.name) else intent
    }

    /** The in-app viewer for [item], or null when its type is not one the viewer can show. */
    fun viewer(item: FileItem): Intent? {
        val kind = ViewerKind.of(item.name, item.mimeType ?: MimeTypes.fromName(item.name)) ?: return null
        return Intent(context, ViewerActivity::class.java)
            .setData(uriFor(item))
            .putExtra(ViewerActivity.EXTRA_NAME, item.name)
            .putExtra(ViewerActivity.EXTRA_KIND, kind.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun share(items: List<FileItem>): Intent {
        val uris = ArrayList(items.map(::uriFor))
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        val mimes = items.map { it.mimeType ?: MimeTypes.fromName(it.name) ?: "*/*" }.distinct()
        intent.type = mimes.singleOrNull() ?: "*/*"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, null)
    }

    /** Opening an APK needs the user to allow UltimateFiles as an install source (Android 8+). */
    fun needsInstallPermission(item: FileItem): Boolean =
        (item.mimeType ?: MimeTypes.fromName(item.name)) == APK_MIME && !context.packageManager.canRequestPackageInstalls()

    /** The system page where the user allows installing apps from UltimateFiles. */
    fun installPermissionSettings(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** There is no public API to unmount a volume; the system storage screen is where it is done. */
    fun ejectSettings(): Intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)

    private fun uriFor(item: FileItem): Uri =
        if (item.path.startsWith("content://")) {
            Uri.parse(item.path)
        } else {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.path))
        }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
