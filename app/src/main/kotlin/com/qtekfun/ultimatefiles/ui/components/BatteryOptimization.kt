package com.qtekfun.ultimatefiles.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qtekfun.ultimatefiles.R

/** True when the system no longer applies battery optimisation (Doze, app standby) to this app. */
fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** Asks the system to exempt the app; falls back to the list of exemptions when the direct dialog is unavailable. */
fun requestIgnoreBatteryOptimizations(context: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(direct)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (ignored: ActivityNotFoundException) {
            // Nothing on this device handles it: the user can still find it in the system settings.
        }
    }
}

/** Live "is the exemption granted" state: re-read whenever the app comes back to the foreground. */
@Composable
fun rememberIgnoringBatteryOptimizations(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = isIgnoringBatteryOptimizations(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

/** Shown once, when the first copy starts: copies with the screen off need the exemption. */
@Composable
fun BatteryHintDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_hint_title)) },
        text = { Text(stringResource(R.string.battery_hint_message)) },
        confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.battery_hint_allow)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.battery_hint_later)) } },
    )
}
