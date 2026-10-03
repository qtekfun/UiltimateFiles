package com.qtekfun.ultimatefiles.domain.repository

import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import kotlinx.coroutines.flow.Flow

/** Connected WebDAV accounts; passwords are kept encrypted and only handed out on request. */
interface AccountRepository {
    val accounts: Flow<List<WebDavAccount>>

    suspend fun passwordOf(accountId: String): String?

    suspend fun add(account: WebDavAccount, password: String)

    suspend fun remove(accountId: String)
}

/** Reversible encryption for secrets at rest (Android Keystore in the app, a stub in tests). */
interface SecretCipher {
    fun encrypt(plain: String): String

    fun decrypt(token: String): String
}
