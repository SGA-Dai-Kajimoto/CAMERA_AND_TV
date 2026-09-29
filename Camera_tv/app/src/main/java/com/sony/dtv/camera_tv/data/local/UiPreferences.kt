package com.sony.dtv.camera_tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_prefs")

/** トークン以外の UI 設定を永続化する。今はチュートリアルの表示済みフラグのみ。 */
class UiPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")

        fun create(context: Context): UiPreferences =
            UiPreferences(context.applicationContext.uiDataStore)
    }

    val tutorialSeen: Flow<Boolean> = dataStore.data.map { it[KEY_TUTORIAL_SEEN] ?: false }

    suspend fun setTutorialSeen() {
        dataStore.edit { it[KEY_TUTORIAL_SEEN] = true }
    }
}
