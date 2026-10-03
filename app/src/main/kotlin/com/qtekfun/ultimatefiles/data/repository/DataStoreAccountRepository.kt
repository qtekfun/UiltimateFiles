package com.qtekfun.ultimatefiles.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.qtekfun.ultimatefiles.core.model.AccountProtocol
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import com.qtekfun.ultimatefiles.domain.repository.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Accounts as tab-separated lines in DataStore; passwords are encrypted with [cipher] before they are stored. */
class DataStoreAccountRepository(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : AccountRepository {

    private class Row(val account: WebDavAccount, val secret: String)

    override val accounts: Flow<List<WebDavAccount>> = store.data.map { prefs -> parse(prefs[KEY]).map { it.account } }.distinctUntilChanged()

    override suspend fun passwordOf(accountId: String): String? {
        val row = parse(store.data.first()[KEY]).firstOrNull { it.account.id == accountId } ?: return null
        return cipher.decrypt(row.secret)
    }

    override suspend fun add(account: WebDavAccount, password: String) {
        store.edit { prefs ->
            val rows = parse(prefs[KEY]).filterNot { it.account.id == account.id } + Row(account, cipher.encrypt(password))
            prefs[KEY] = serialize(rows)
        }
    }

    override suspend fun remove(accountId: String) {
        store.edit { prefs -> prefs[KEY] = serialize(parse(prefs[KEY]).filterNot { it.account.id == accountId }) }
    }

    private fun serialize(rows: List<Row>): String = rows.joinToString("\n") { row ->
        listOf(
            row.account.id,
            row.account.label,
            row.account.baseUrl,
            row.account.username,
            row.secret,
            row.account.pinnedCertSha256.orEmpty(),
            row.account.allowInsecureHttp.toString(),
            row.account.protocol.name,
        ).joinToString("\t") { Tsv.escape(it) }
    }

    private fun parse(text: String?): List<Row> = text.orEmpty().lines().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size < 5) {
            null
        } else {
            // Older lines have five columns; the pin and the HTTP flag were added later.
            val pinned = f.getOrNull(5)?.let(Tsv::unescape)?.takeIf { it.isNotEmpty() }
            val insecure = f.getOrNull(6)?.let(Tsv::unescape) == "true"
            val protocol = f.getOrNull(7)?.let(Tsv::unescape)?.let { name -> AccountProtocol.entries.firstOrNull { it.name == name } }
                ?: AccountProtocol.WEBDAV
            Row(
                WebDavAccount(Tsv.unescape(f[0]), Tsv.unescape(f[1]), Tsv.unescape(f[2]), Tsv.unescape(f[3]), pinned, insecure, protocol),
                Tsv.unescape(f[4]),
            )
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("webdav_accounts")
    }
}
