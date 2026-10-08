package com.qtekfun.ultimatefiles.domain.repository

import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import kotlinx.coroutines.flow.Flow

/** Connected WebDAV accounts; passwords are kept encrypted and only handed out on request. */
interface AccountRepository {
    val accounts: Flow<List<WebDavAccount>>

    suspend fun passwordOf(accountId: String): String?

    suspend fun add(account: WebDavAccount, password: String)

    suspend fun remove(accountId: String)

    /**
     * Replaces the stored data of the account with [account]'s id, keeping its place in the list. The stored secret is
     * kept when [password] is null and replaced otherwise. An id that is not stored changes nothing.
     */
    suspend fun update(account: WebDavAccount, password: String?)

    /** Changes only the name shown for the account; its password and settings are untouched. */
    suspend fun rename(accountId: String, label: String)
}

/** Reversible encryption for secrets at rest (Android Keystore in the app, a stub in tests). */
interface SecretCipher {
    fun encrypt(plain: String): String

    fun decrypt(token: String): String
}
