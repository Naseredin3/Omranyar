package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiModelMode
import com.example.data.model.AttachmentInput
import com.example.data.model.DocumentStatuses
import com.example.data.model.SourceTypes
import com.example.data.model.TechnicalDocument
import com.example.data.repository.OmranAiRepository
import com.example.data.repository.OmranFirestoreRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data object Analyzing : AnalysisUiState
    data class Success(val document: TechnicalDocument) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

class AnalyzerViewModel(
    private val firestoreRepository: OmranFirestoreRepository,
    private val aiRepository: OmranAiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedMode = MutableStateFlow(AiModelMode.PRECISION)
    val selectedMode: StateFlow<AiModelMode> = _selectedMode.asStateFlow()

    private val _attachment = MutableStateFlow<AttachmentInput?>(null)
    val attachment: StateFlow<AttachmentInput?> = _attachment.asStateFlow()

    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    fun setAiMode(mode: AiModelMode) {
        _selectedMode.value = mode
    }

    fun setAttachment(input: AttachmentInput) {
        _attachment.value = input
        if (_uiState.value is AnalysisUiState.Error) {
            _uiState.value = AnalysisUiState.Idle
        }
    }

    fun clearAttachment() {
        _attachment.value = null
    }

    fun resetResult() {
        _uiState.value = AnalysisUiState.Idle
    }

    fun analyzeAndArchive(textInput: String) {
        val currentAttachment = _attachment.value
        if (textInput.isBlank() && currentAttachment == null) {
            _uiState.value = AnalysisUiState.Error("لطفاً متن گزارش فنی را وارد کنید یا یک تصویر/فایل پیوست نمایید.")
            return
        }

        viewModelScope.launch {
            _uiState.value = AnalysisUiState.Analyzing
            try {
                val analysis = aiRepository.analyzeDocument(
                    textInput = textInput,
                    attachment = currentAttachment,
                    mode = _selectedMode.value
                )

                val sourceType = when (currentAttachment) {
                    is AttachmentInput.ImageAttachment -> SourceTypes.IMAGE
                    is AttachmentInput.DocumentFileAttachment -> SourceTypes.FILE
                    null -> SourceTypes.TEXT
                }

                val excerpt = buildString {
                    if (currentAttachment != null) {
                        append("پیوست: ${currentAttachment.displayName}")
                        if (textInput.isNotBlank()) append(" — ")
                    }
                    append(textInput.trim())
                }.take(3900)

                val docToSave = TechnicalDocument(
                    userId = currentUserId,
                    title = analysis.title,
                    category = analysis.category,
                    priority = analysis.priority,
                    status = DocumentStatuses.UNDER_REVIEW,
                    projectArea = analysis.projectArea,
                    sourceType = sourceType,
                    originalExcerpt = excerpt,
                    summary = analysis.summary,
                    keyFindings = analysis.keyFindings,
                    actionItems = analysis.actionItems,
                    risksOrIssues = analysis.risksOrIssues,
                    tags = analysis.tags
                )

                val createResult = firestoreRepository.createDocument(currentUserId, docToSave)
                if (createResult.isSuccess) {
                    val savedId = createResult.getOrThrow()
                    _uiState.value = AnalysisUiState.Success(docToSave.copy(id = savedId))
                    _attachment.value = null
                } else {
                    val err = createResult.exceptionOrNull()
                    _uiState.value = AnalysisUiState.Error(
                        err?.localizedMessage ?: "تحلیل انجام شد اما ذخیره در بایگانی با خطا مواجه شد."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Document analysis timed out", e)
                _uiState.value = AnalysisUiState.Error("زمان تحلیل سند به پایان رسید (۶۰ ثانیه). لطفاً دوباره تلاش کنید.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Document analysis failed", e)
                _uiState.value = AnalysisUiState.Error(
                    e.localizedMessage ?: "خطا در تحلیل هوشمند مستندات فنی."
                )
            }
        }
    }

    private companion object {
        const val TAG = "AnalyzerViewModel"
    }
}
