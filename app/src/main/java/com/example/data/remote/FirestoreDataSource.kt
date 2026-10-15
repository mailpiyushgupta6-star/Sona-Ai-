package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.remote.model.CloudConversationDto
import com.example.data.remote.model.CloudMemoryDto
import com.example.data.remote.model.CloudMessageDto
import com.example.data.remote.model.CloudSettingsDto
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

open class FirestoreDataSource(
    val db: FirebaseFirestore? = null
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth get() = Firebase.auth

    private fun requireUserId(expectedUserId: String? = null): String {
        val currentUid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be authenticated before accessing Firestore.")
        if (expectedUserId != null && currentUid != expectedUserId) {
            throw IllegalStateException("Authenticated user $currentUid does not match expected $expectedUserId")
        }
        return currentUid
    }

    // --- Conversations ---

    open suspend fun saveConversation(userId: String, conv: CloudConversationDto) {
        val uid = requireUserId(userId)
        val path = "users/$uid/conversations/${conv.id}"
        val docRef = db?.collection("users")?.document(uid)?.collection("conversations")?.document(conv.id)
            ?: throw IllegalStateException("Firestore database instance is null")
        try {
            docRef.set(conv.toMap()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            throw e
        }
    }

    open fun observeConversations(userId: String): Flow<List<CloudConversationDto>> {
        val uid = requireUserId(userId)
        val path = "users/$uid/conversations"
        val database = db ?: throw IllegalStateException("Firestore database instance is null")
        return database.collection("users").document(uid).collection("conversations")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(CloudConversationDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    open suspend fun getConversations(userId: String): List<CloudConversationDto> {
        val uid = requireUserId(userId)
        val path = "users/$uid/conversations"
        val database = db ?: throw IllegalStateException("Firestore database instance is null")
        return try {
            val snapshot = database.collection("users").document(uid).collection("conversations").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(CloudConversationDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            throw e
        }
    }

    open suspend fun deleteConversation(userId: String, conversationId: String) {
        val uid = requireUserId(userId)
        val path = "users/$uid/conversations/$conversationId"
        val docRef = db?.collection("users")?.document(uid)?.collection("conversations")?.document(conversationId)
            ?: throw IllegalStateException("Firestore database instance is null")
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    // --- Messages ---

    open suspend fun saveMessage(userId: String, message: CloudMessageDto) {
        val uid = requireUserId(userId)
        val path = "users/$uid/conversations/${message.conversationId}/messages/${message.id}"
        val docRef = db?.collection("users")?.document(uid)
            ?.collection("conversations")?.document(message.conversationId)
            ?.collection("messages")?.document(message.id)
            ?: throw IllegalStateException("Firestore database instance is null")
        try {
            docRef.set(message.toMap()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            throw e
        }
    }

    open fun observeMessages(userId: String, conversationId: String): Flow<List<CloudMessageDto>> {
        val uid = requireUserId(userId)
        val path = "users/$uid/conversations/$conversationId/messages"
        val database = db ?: throw IllegalStateException("Firestore database instance is null")
        return database.collection("users").document(uid)
            .collection("conversations").document(conversationId)
            .collection("messages")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(CloudMessageDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    open suspend fun getMessages(userId: String, conversationId: String): List<CloudMessageDto> {
        val uid = requireUserId(userId)
        val path = "users/$uid/conversations/$conversationId/messages"
        val database = db ?: throw IllegalStateException("Firestore database instance is null")
        return try {
            val snapshot = database.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(CloudMessageDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            throw e
        }
    }

    // --- Memories ---

    open suspend fun saveMemory(userId: String, memory: CloudMemoryDto) {
        val uid = requireUserId(userId)
        val path = "users/$uid/memories/${memory.id}"
        val docRef = db?.collection("users")?.document(uid)?.collection("memories")?.document(memory.id)
            ?: throw IllegalStateException("Firestore database instance is null")
        try {
            docRef.set(memory.toMap()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            throw e
        }
    }

    open fun observeMemories(userId: String): Flow<List<CloudMemoryDto>> {
        val uid = requireUserId(userId)
        val path = "users/$uid/memories"
        val database = db ?: throw IllegalStateException("Firestore database instance is null")
        return database.collection("users").document(uid).collection("memories")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(CloudMemoryDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    open suspend fun deleteMemory(userId: String, memoryId: String) {
        val uid = requireUserId(userId)
        val path = "users/$uid/memories/$memoryId"
        val docRef = db?.collection("users")?.document(uid)?.collection("memories")?.document(memoryId)
            ?: throw IllegalStateException("Firestore database instance is null")
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    // --- Settings ---

    open suspend fun saveSettings(userId: String, settings: CloudSettingsDto) {
        val uid = requireUserId(userId)
        val path = "users/$uid/settings/general"
        val docRef = db?.collection("users")?.document(uid)?.collection("settings")?.document("general")
            ?: throw IllegalStateException("Firestore database instance is null")
        try {
            docRef.set(settings.toMap()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            throw e
        }
    }

    open fun observeSettings(userId: String): Flow<CloudSettingsDto?> {
        val uid = requireUserId(userId)
        val path = "users/$uid/settings/general"
        val database = db ?: throw IllegalStateException("Firestore database instance is null")
        return database.collection("users").document(uid).collection("settings").document("general")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(CloudSettingsDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }
}
