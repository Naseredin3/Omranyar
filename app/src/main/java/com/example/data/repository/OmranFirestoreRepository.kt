package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentCategories
import com.example.data.model.DocumentPriorities
import com.example.data.model.DocumentStatuses
import com.example.data.model.SourceTypes
import com.example.data.model.TechnicalDocument
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class OmranFirestoreRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeDocuments(userId: String): Flow<List<TechnicalDocument>> {
        val path = "users/$userId/documents"
        return db.collection("users")
            .document(userId)
            .collection("documents")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    TechnicalDocument::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun getDocumentById(userId: String, documentId: String): Result<TechnicalDocument?> {
        val docRef = db.collection("users").document(userId).collection("documents").document(documentId)
        return try {
            val snapshot = docRef.get().await()
            val doc = snapshot.toObject(
                TechnicalDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(doc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createDocument(userId: String, document: TechnicalDocument): Result<String> {
        val collectionRef = db.collection("users").document(userId).collection("documents")
        val docRef = collectionRef.document()

        val safeCategory = if (document.category in DocumentCategories.all) {
            document.category
        } else {
            DocumentCategories.OTHER
        }
        val safePriority = if (document.priority in DocumentPriorities.all) {
            document.priority
        } else {
            DocumentPriorities.NORMAL
        }
        val safeStatus = if (document.status in DocumentStatuses.all) {
            document.status
        } else {
            DocumentStatuses.UNDER_REVIEW
        }
        val safeSourceType = if (document.sourceType in listOf(SourceTypes.TEXT, SourceTypes.IMAGE, SourceTypes.FILE)) {
            document.sourceType
        } else {
            SourceTypes.TEXT
        }

        val payload = mapOf(
            "userId" to userId,
            "title" to document.title.trim().take(200).ifEmpty { "سند فنی بدون عنوان" },
            "category" to safeCategory,
            "priority" to safePriority,
            "status" to safeStatus,
            "projectArea" to document.projectArea.trim().take(120).ifEmpty { "عمومی سازمان عمران" },
            "sourceType" to safeSourceType,
            "originalExcerpt" to document.originalExcerpt.trim().take(4000),
            "summary" to document.summary.trim().take(2000).ifEmpty { "خلاصه ثبت نشده است." },
            "keyFindings" to document.keyFindings.map { it.trim() }.filter { it.isNotEmpty() }.take(10),
            "actionItems" to document.actionItems.map { it.trim() }.filter { it.isNotEmpty() }.take(10),
            "risksOrIssues" to document.risksOrIssues.map { it.trim() }.filter { it.isNotEmpty() }.take(10),
            "tags" to document.tags.map { it.trim() }.filter { it.isNotEmpty() }.take(10),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateDocumentStatusAndCategory(
        userId: String,
        documentId: String,
        status: String,
        priority: String,
        category: String
    ): Result<Unit> {
        require(documentId.isNotBlank()) { "Document ID must not be blank" }
        val docRef = db.collection("users").document(userId).collection("documents").document(documentId)
        val updates = mapOf(
            "status" to status,
            "priority" to priority,
            "category" to category,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(userId: String, documentId: String): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("documents").document(documentId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeChatMessages(userId: String): Flow<List<ChatMessage>> {
        val path = "users/$userId/chatMessages"
        return db.collection("users")
            .document(userId)
            .collection("chatMessages")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun addChatMessage(
        userId: String,
        role: String,
        text: String,
        attachmentName: String? = null,
        modelUsed: String? = null
    ): Result<String> {
        val docRef = db.collection("users").document(userId).collection("chatMessages").document()
        val payload = buildMap<String, Any> {
            put("userId", userId)
            put("role", if (role == "model") "model" else "user")
            put("text", text.trim().take(12000).ifEmpty { "..." })
            if (!attachmentName.isNullOrBlank()) {
                put("attachmentName", attachmentName.trim().take(200))
            }
            if (!modelUsed.isNullOrBlank()) {
                put("modelUsed", modelUsed.trim().take(80))
            }
            put("createdAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearChatHistory(userId: String): Result<Unit> {
        val colRef = db.collection("users").document(userId).collection("chatMessages")
        return try {
            val snapshot = colRef.get().await()
            if (!snapshot.isEmpty) {
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            Result.failure(e)
        }
    }
}
