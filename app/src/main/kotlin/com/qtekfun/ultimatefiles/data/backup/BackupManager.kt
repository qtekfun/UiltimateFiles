package com.qtekfun.ultimatefiles.data.backup

import com.qtekfun.ultimatefiles.core.model.SortField
import com.qtekfun.ultimatefiles.core.model.SortOrder
import com.qtekfun.ultimatefiles.core.model.ThemeMode
import com.qtekfun.ultimatefiles.core.model.ViewMode
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import com.qtekfun.ultimatefiles.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class BackupProblem { INVALID_FILE, UNSUPPORTED_VERSION, NEEDS_PASSPHRASE, WRONG_PASSPHRASE }

class BackupException(val problem: BackupProblem) : Exception(problem.name)

/** What an import changed. */
data class ImportSummary(val settingsApplied: Boolean, val accountsAdded: Int)

/**
 * Exports the preferences and the connected accounts to one JSON document and restores them. The accounts block holds
 * the app passwords, so it is encrypted with a key derived from a passphrase the user chooses (the Android Keystore
 * key that protects them on the device cannot leave it). Folders last opened are device specific and are not saved.
 */
class BackupManager(
    private val preferences: UserPreferencesRepository,
    private val accounts: AccountRepository,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val kdfIterations: Int = DEFAULT_KDF_ITERATIONS,
) {
    /** How many accounts a backup could carry, so the UI only asks for a passphrase when it is needed. */
    val accountCount: Flow<Int> = accounts.accounts.map { it.size }

    suspend fun export(includeAccounts: Boolean, passphrase: String?): String {
        val prefs = preferences.preferences.first()
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", clockMillis())
            .put(
                "settings",
                JSONObject()
                    .put("themeMode", prefs.themeMode.name)
                    .put("dynamicColor", prefs.dynamicColor)
                    .put("verifyCopies", prefs.verifyCopies)
                    .put("viewMode", prefs.viewMode.name)
                    .put("sortField", prefs.sortOrder.field.name)
                    .put("sortAscending", prefs.sortOrder.ascending),
            )
        val list = if (includeAccounts) accounts.accounts.first() else emptyList()
        if (list.isNotEmpty()) {
            if (passphrase.isNullOrEmpty()) throw BackupException(BackupProblem.NEEDS_PASSPHRASE)
            val plain = JSONArray()
            list.forEach { account ->
                plain.put(
                    JSONObject()
                        .put("id", account.id)
                        .put("label", account.label)
                        .put("baseUrl", account.baseUrl)
                        .put("username", account.username)
                        .put("password", accounts.passwordOf(account.id).orEmpty()),
                )
            }
            root.put("accounts", seal(plain.toString(), passphrase))
        }
        return root.toString(2)
    }

    suspend fun import(text: String, passphrase: String?): ImportSummary {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException(BackupProblem.INVALID_FILE)
        }
        if (root.optString("format") != FORMAT) throw BackupException(BackupProblem.INVALID_FILE)
        if (root.optInt("version", 0) != VERSION) throw BackupException(BackupProblem.UNSUPPORTED_VERSION)

        // Decrypt before touching anything so a wrong passphrase changes nothing.
        val decrypted = root.optJSONObject("accounts")?.let { block ->
            if (passphrase.isNullOrEmpty()) throw BackupException(BackupProblem.NEEDS_PASSPHRASE)
            JSONArray(open(block, passphrase))
        }

        var settingsApplied = false
        root.optJSONObject("settings")?.let { s ->
            enumOrNull<ThemeMode>(s.optString("themeMode"))?.let { preferences.setThemeMode(it) }
            if (s.has("dynamicColor")) preferences.setDynamicColor(s.getBoolean("dynamicColor"))
            if (s.has("verifyCopies")) preferences.setVerifyCopies(s.getBoolean("verifyCopies"))
            enumOrNull<ViewMode>(s.optString("viewMode"))?.let { preferences.setViewMode(it) }
            enumOrNull<SortField>(s.optString("sortField"))?.let { field ->
                preferences.setSortOrder(SortOrder(field, s.optBoolean("sortAscending", true)))
            }
            settingsApplied = true
        }

        var added = 0
        if (decrypted != null) {
            for (i in 0 until decrypted.length()) {
                val row = decrypted.getJSONObject(i)
                val account = WebDavAccount(row.getString("id"), row.getString("label"), row.getString("baseUrl"), row.getString("username"))
                accounts.add(account, row.getString("password")) // same id: replaces, so importing twice is harmless
                added++
            }
        }
        return ImportSummary(settingsApplied, added)
    }

    private fun seal(plain: String, passphrase: String): JSONObject {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key(passphrase, salt, kdfIterations), GCMParameterSpec(TAG_BITS, iv))
        }
        return JSONObject()
            .put("kdf", KDF)
            .put("iterations", kdfIterations)
            .put("salt", b64(salt))
            .put("iv", b64(iv))
            .put("data", b64(cipher.doFinal(plain.toByteArray(Charsets.UTF_8))))
    }

    private fun open(block: JSONObject, passphrase: String): String = try {
        val salt = Base64.getDecoder().decode(block.getString("salt"))
        val iv = Base64.getDecoder().decode(block.getString("iv"))
        val data = Base64.getDecoder().decode(block.getString("data"))
        val iterations = block.getInt("iterations").also { if (it !in 1..MAX_ITERATIONS) throw BackupException(BackupProblem.INVALID_FILE) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        }
        String(cipher.doFinal(data), Charsets.UTF_8)
    } catch (e: BackupException) {
        throw e
    } catch (e: GeneralSecurityException) {
        throw BackupException(BackupProblem.WRONG_PASSPHRASE) // GCM tag mismatch: wrong passphrase or tampered file
    } catch (e: JSONException) {
        throw BackupException(BackupProblem.INVALID_FILE)
    } catch (e: IllegalArgumentException) {
        throw BackupException(BackupProblem.INVALID_FILE)
    }

    private fun key(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }

    companion object {
        const val FORMAT = "UltimateFiles backup"
        const val VERSION = 1
        const val DEFAULT_KDF_ITERATIONS = 210_000
        private const val MAX_ITERATIONS = 5_000_000
        private const val KDF = "PBKDF2WithHmacSHA256"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
    }
}
