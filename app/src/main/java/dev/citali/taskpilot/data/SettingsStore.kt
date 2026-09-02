package dev.citali.taskpilot.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "taskpilot_settings")

/**
 * User-tunable TaskPilot settings, persisted with Jetpack DataStore.
 *
 * Nothing in this file is secret. The API key is handled separately in
 * [SecureStore] and never touches this store.
 */
data class TaskPilotSettings(
    val endpointUrl: String = DEFAULT_ENDPOINT,
    val model: String = DEFAULT_MODEL,
    val highRiskConfirmations: Boolean = true,
    val redactSensitiveValues: Boolean = true,
    val pauseOnAmbiguity: Boolean = true,
    val showTreeSummary: Boolean = true,
    val showValidation: Boolean = true,
    val showOverlay: Boolean = true,
) {
    companion object {
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}

object SettingsStore {
    private val KEY_ENDPOINT = stringPreferencesKey("endpoint_url")
    private val KEY_MODEL = stringPreferencesKey("model")
    private val KEY_HIGH_RISK_CONFIRM = booleanPreferencesKey("high_risk_confirmations")
    private val KEY_REDACT = booleanPreferencesKey("redact_sensitive_values")
    private val KEY_PAUSE_AMBIGUITY = booleanPreferencesKey("pause_on_ambiguity")
    private val KEY_SHOW_TREE = booleanPreferencesKey("show_tree_summary")
    private val KEY_SHOW_VALIDATION = booleanPreferencesKey("show_validation")
    private val KEY_SHOW_OVERLAY = booleanPreferencesKey("show_overlay")

    fun settings(context: Context): Flow<TaskPilotSettings> =
        context.settingsDataStore.data.map { p ->
            TaskPilotSettings(
                endpointUrl = p[KEY_ENDPOINT] ?: TaskPilotSettings.DEFAULT_ENDPOINT,
                model = p[KEY_MODEL] ?: TaskPilotSettings.DEFAULT_MODEL,
                highRiskConfirmations = p[KEY_HIGH_RISK_CONFIRM] ?: true,
                redactSensitiveValues = p[KEY_REDACT] ?: true,
                pauseOnAmbiguity = p[KEY_PAUSE_AMBIGUITY] ?: true,
                showTreeSummary = p[KEY_SHOW_TREE] ?: true,
                showValidation = p[KEY_SHOW_VALIDATION] ?: true,
                showOverlay = p[KEY_SHOW_OVERLAY] ?: true,
            )
        }

    suspend fun snapshot(context: Context): TaskPilotSettings = settings(context).first()

    suspend fun setProvider(context: Context, endpoint: String, model: String) {
        context.settingsDataStore.edit { p ->
            p[KEY_ENDPOINT] = endpoint.trim()
            p[KEY_MODEL] = model.trim()
        }
    }

    suspend fun setHighRiskConfirmations(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[KEY_HIGH_RISK_CONFIRM] = value }
    }

    suspend fun setRedactSensitiveValues(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[KEY_REDACT] = value }
    }

    suspend fun setPauseOnAmbiguity(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[KEY_PAUSE_AMBIGUITY] = value }
    }

    suspend fun setShowTreeSummary(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_TREE] = value }
    }

    suspend fun setShowValidation(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_VALIDATION] = value }
    }

    suspend fun setShowOverlay(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_OVERLAY] = value }
    }
}
