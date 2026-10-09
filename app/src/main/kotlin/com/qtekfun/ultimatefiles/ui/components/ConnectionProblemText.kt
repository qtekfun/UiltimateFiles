package com.qtekfun.ultimatefiles.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind

@StringRes
private fun ConnectionProblemKind.messageRes(): Int = when (this) {
    ConnectionProblemKind.OFFLINE -> R.string.connection_problem_offline
    ConnectionProblemKind.NO_RESPONSE -> R.string.connection_problem_no_response
    ConnectionProblemKind.AUTH -> R.string.connection_problem_auth
    ConnectionProblemKind.IDENTITY -> R.string.connection_problem_identity
    ConnectionProblemKind.NOT_FOUND -> R.string.connection_problem_not_found
    ConnectionProblemKind.OTHER -> R.string.connection_problem_other
}

@StringRes
private fun ConnectionProblemKind.shortRes(): Int = when (this) {
    ConnectionProblemKind.OFFLINE -> R.string.connection_short_offline
    ConnectionProblemKind.NO_RESPONSE -> R.string.connection_short_no_response
    ConnectionProblemKind.AUTH -> R.string.connection_short_auth
    ConnectionProblemKind.IDENTITY -> R.string.connection_short_identity
    ConnectionProblemKind.NOT_FOUND -> R.string.connection_short_not_found
    ConnectionProblemKind.OTHER -> R.string.connection_short_other
}

/** The sentence that tells the user why a server could not be used, naming the account (or "this server" when unknown). */
fun Resources.connectionMessage(kind: ConnectionProblemKind, accountLabel: String?, detail: String? = null): String {
    val name = accountLabel ?: getString(R.string.connection_this_server)
    return if (kind == ConnectionProblemKind.OTHER) getString(kind.messageRes(), name, detail.orEmpty()) else getString(kind.messageRes(), name)
}

/** A few words for the drawer, where the account's name is already next to it. */
fun Resources.connectionShortReason(kind: ConnectionProblemKind): String = getString(kind.shortRes())

/**
 * A Snackbar message with up to two actions: [retryLabel] (reported as `ActionPerformed`) and [editLabel], whose button is
 * handled by the host through the callback it was given.
 */
class ConnectionSnackbarVisuals(
    override val message: String,
    private val retryLabel: String?,
    val editLabel: String?,
    /** The account the buttons act on; the edit button only exists when it is known. */
    val accountId: String?,
) : SnackbarVisuals {
    override val actionLabel: String? get() = retryLabel
    override val withDismissAction: Boolean get() = false
    override val duration: SnackbarDuration get() = SnackbarDuration.Long
}

/** Draws [data]; a [ConnectionSnackbarVisuals] gets its two buttons, anything else the standard Snackbar. */
@Composable
fun ConnectionAwareSnackbar(data: SnackbarData, onEditAccount: (accountId: String) -> Unit) {
    val visuals = data.visuals
    if (visuals !is ConnectionSnackbarVisuals) {
        Snackbar(data)
        return
    }
    Snackbar(
        action = {
            Row {
                val accountId = visuals.accountId
                visuals.editLabel?.takeIf { accountId != null }?.let { label ->
                    TextButton(onClick = {
                        data.dismiss()
                        onEditAccount(accountId!!)
                    }) { Text(label) }
                }
                visuals.actionLabel?.let { label -> TextButton(onClick = { data.performAction() }) { Text(label) } }
            }
        },
    ) { Text(visuals.message) }
}
