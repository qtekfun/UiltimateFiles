package com.qtekfun.ultimatefiles.ui.components

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qtekfun.ultimatefiles.data.service.TransferNotifications

/** The value of [check], read again every time the app comes back to the foreground (the user may have just fixed it). */
@Composable
fun rememberCheck(check: () -> Boolean): State<Boolean> {
    val state = remember { mutableStateOf(check()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = check()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

/** A problem with the system setup, shown only while it exists: why it matters and a button to the place that fixes it. */
@Composable
fun CheckNotice(ok: Boolean, message: Int, action: Int, onAction: () -> Unit) {
    if (ok) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onAction) { Text(stringResource(action)) }
    }
}

/** False when the user turned off all notifications for the app (then no progress is visible at all). */
fun areNotificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

/** True when the progress channel exists and was not silenced to the point of not showing (importance "none"). */
fun isProgressChannelUsable(context: Context): Boolean {
    val channel = context.getSystemService(NotificationManager::class.java)
        ?.getNotificationChannel(TransferNotifications.CHANNEL_ID) ?: return true // not created yet: it will be on first use
    return channel.importance != NotificationManager.IMPORTANCE_NONE
}

fun openAppNotificationSettings(context: Context) = launchSettings(
    context,
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
)

/** The app's own page in the system settings: battery usage, auto-launch, permissions. */
fun openAppDetails(context: Context) = launchSettings(
    context,
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
)

private fun launchSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Nothing on this device handles it; the user can still reach the settings by hand.
    }
}

/**
 * Display name of the phone maker when it is known for closing background apps harder than Android itself does (the
 * system exemption is not enough there: the maker's own battery manager must be told too); otherwise null.
 */
fun aggressiveBatteryVendor(manufacturer: String = Build.MANUFACTURER): String? =
    when (manufacturer.lowercase()) {
        "oppo" -> "OPPO"
        "realme" -> "realme"
        "oneplus" -> "OnePlus"
        "xiaomi" -> "Xiaomi"
        "redmi" -> "Redmi"
        "poco" -> "POCO"
        "huawei" -> "Huawei"
        "honor" -> "Honor"
        "vivo" -> "vivo"
        "iqoo" -> "iQOO"
        "samsung" -> "Samsung"
        else -> null
    }
