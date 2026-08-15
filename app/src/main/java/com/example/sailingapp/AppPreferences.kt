package com.example.sailingapp

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.profileStore by preferencesDataStore(name = "profile_prefs")

class AppPreferences(private val context: Context) {

    private val PROFILE_KEY = stringPreferencesKey("active_profile")

    suspend fun getActiveProfile(): SailingProfile {
        val prefs = context.profileStore.data.first()
        val label = prefs[PROFILE_KEY]
        return if (label != null) {
            SailingProfile.fromLabel(label)
        } else {
            SailingProfile.CROCIERA  // Default
        }
    }

    suspend fun saveActiveProfile(profile: SailingProfile) {
        context.profileStore.edit { prefs ->
            prefs[PROFILE_KEY] = profile.label
        }
    }
}