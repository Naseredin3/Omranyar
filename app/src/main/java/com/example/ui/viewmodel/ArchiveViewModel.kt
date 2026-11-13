package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DocumentPriorities
import com.example.data.model.DocumentStatuses
import com.example.data.model.TechnicalDocument
import com.example.data.repository.OmranFirestoreRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ArchiveUiState {
    data object Loading : ArchiveUiState
    data class Success(
        val allDocuments: List<TechnicalDocument>,
        val filteredDocuments: List<TechnicalDocument>,
        val categoryCounts: Map<String, Int>,
        val urgentCount: Int,
        val approvedCount: Int
    ) : ArchiveUiState
    data class Error(val message: String) : ArchiveUiState
}

class ArchiveViewModel(
    private val firestoreRepository: OmranFirestoreRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val documentsStream = firestoreRepository.observeDocuments(currentUserId)
        .map<List<TechnicalDocument>, Result<List<TechnicalDocument>>> { Result.success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing archived documents", rootError)
            emit(Result.failure(rootError))
        }

    val uiState: StateFlow<ArchiveUiState> = combine(
        documentsStream,
        _searchQuery,
        _selectedCategory
    ) { docsResult, query, category ->
        docsResult.fold(
            onSuccess = { docs ->
                val q = query.trim()
                val filtered = docs.filter { doc ->
                    val matchesCategory = category == null || doc.category == category
                    val matchesQuery = q.isEmpty() ||
                        doc.title.contains(q, ignoreCase = true) ||
                        doc.summary.contains(q, ignoreCase = true) ||
                        doc.projectArea.contains(q, ignoreCase = true) ||
                        doc.tags.any { tag -> tag.contains(q, ignoreCase = true) }
                    matchesCategory && matchesQuery
                }
                val counts = docs.groupingBy { it.category }.eachCount()
                val urgent = docs.count { it.priority == DocumentPriorities.URGENT }
                val approved = docs.count { it.status == DocumentStatuses.APPROVED }
                ArchiveUiState.Success(
                    allDocuments = docs,
                    filteredDocuments = filtered,
                    categoryCounts = counts,
                    urgentCount = urgent,
                    approvedCount = approved
                )
            },
            onFailure = { error ->
                ArchiveUiState.Error(error.message ?: "خطا در دریافت بایگانی مستندات")
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ArchiveUiState.Loading
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun updateDocumentMeta(
        documentId: String,
        newStatus: String,
        newPriority: String,
        newCategory: String
    ) {
        viewModelScope.launch {
            val result = firestoreRepository.updateDocumentStatusAndCategory(
                userId = currentUserId,
                documentId = documentId,
                status = newStatus,
                priority = newPriority,
                category = newCategory
            )
            if (result.isFailure) {
                _actionError.value = result.exceptionOrNull()?.localizedMessage ?: "به‌روزرسانی وضعیت سند انجام نشد."
            }
        }
    }

    fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            val result = firestoreRepository.deleteDocument(currentUserId, documentId)
            if (result.isFailure) {
                _actionError.value = result.exceptionOrNull()?.localizedMessage ?: "حذف سند انجام نشد."
            }
        }
    }

    private companion object {
        const val TAG = "ArchiveViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
