package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getLastActiveConversationId(): String? {
        return prefs.getString(KEY_LAST_CONVERSATION_ID, null)
    }

    fun setLastActiveConversationId(conversationId: String?) {
        prefs.edit().apply {
            if (conversationId != null) {
                putString(KEY_LAST_CONVERSATION_ID, conversationId)
            } else {
                remove(KEY_LAST_CONVERSATION_ID)
            }
            apply()
        }
    }

    fun getLastAuthUserId(): String? {
        return prefs.getString(KEY_LAST_AUTH_USER_ID, null)
    }

    fun setLastAuthUserId(userId: String?) {
        prefs.edit().apply {
            if (userId != null) {
                putString(KEY_LAST_AUTH_USER_ID, userId)
            } else {
                remove(KEY_LAST_AUTH_USER_ID)
            }
            apply()
        }
    }

    fun clearActiveConversation() {
        prefs.edit().remove(KEY_LAST_CONVERSATION_ID).apply()
    }

    fun getStorageLocation(): String {
        return prefs.getString(KEY_STORAGE_LOCATION, STORAGE_CLOUD_FIRESTORE) ?: STORAGE_CLOUD_FIRESTORE
    }

    fun setStorageLocation(location: String) {
        prefs.edit().putString(KEY_STORAGE_LOCATION, location).apply()
    }

    companion object {
        private const val PREFS_NAME = "sona_app_preferences"
        private const val KEY_LAST_CONVERSATION_ID = "last_active_conversation_id"
        private const val KEY_LAST_AUTH_USER_ID = "last_auth_user_id"
        private const val KEY_STORAGE_LOCATION = "storage_location"
        const val STORAGE_CLOUD_FIRESTORE = "CLOUD_FIRESTORE"
        const val STORAGE_PHONE_STORAGE = "PHONE_STORAGE"
    }
}
