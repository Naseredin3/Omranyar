package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiModelMode
import com.example.data.model.AttachmentInput
import com.example.data.model.ChatMessage
import com.example.data.repository.OmranAiRepository
import com.example.data.repository.OmranFirestoreRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ChatHistoryState {
    data object Loading : ChatHistoryState
    data class Success(val messages: List<ChatMessage>) : ChatHistoryState
    data class Error(val message: String) : ChatHistoryState
}

class ChatViewModel(
    private val firestoreRepository: OmranFirestoreRepository,
    private val aiRepository: OmranAiRepository,
    private val currentUserId: String
) : ViewModel() {

    val messagesState: StateFlow<ChatHistoryState> = firestoreRepository.observeChatMessages(currentUserId)
        .map<List<ChatMessage>, ChatHistoryState> { ChatHistoryState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing chat messages", rootError)
            emit(ChatHistoryState.Error(rootError.message ?: "خطا در دریافت تاریخچه گفتگو"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ChatHistoryState.Loading
        )

    private val _selectedMode = MutableStateFlow(AiModelMode.PRECISION)
    val selectedMode: StateFlow<AiModelMode> = _selectedMode.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<AttachmentInput?>(null)
    val pendingAttachment: StateFlow<AttachmentInput?> = _pendingAttachment.asStateFlow()

    private val _streamingResponse = MutableStateFlow<String?>(null)
    val streamingResponse: StateFlow<String?> = _streamingResponse.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setAiMode(mode: AiModelMode) {
        _selectedMode.value = mode
    }

    fun attachInput(attachment: AttachmentInput) {
        _pendingAttachment.value = attachment
        _errorMessage.value = null
    }

    fun clearAttachment() {
        _pendingAttachment.value = null
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun sendMessage(promptText: String) {
        val trimmedPrompt = promptText.trim()
        val attachment = _pendingAttachment.value
        if (trimmedPrompt.isEmpty() && attachment == null) return
        if (_isGenerating.value) return

        val mode = _selectedMode.value
        val existingHistory = (messagesState.value as? ChatHistoryState.Success)?.messages ?: emptyList()
        val displayUserText = trimmedPrompt.ifBlank { "تحلیل فایل پیوست: ${attachment?.displayName.orEmpty()}" }

        viewModelScope.launch {
            _isGenerating.value = true
            _errorMessage.value = null
            _streamingResponse.value = ""
            _pendingAttachment.value = null

            val saveUserResult = firestoreRepository.addChatMessage(
                userId = currentUserId,
                role = "user",
                text = displayUserText,
                attachmentName = attachment?.displayName
            )
            if (saveUserResult.isFailure) {
                Log.w(TAG, "Failed saving user message", saveUserResult.exceptionOrNull())
            }

            var latestText = ""
            try {
                aiRepository.streamChatResponse(
                    history = existingHistory,
                    userPrompt = trimmedPrompt,
                    attachment = attachment,
                    mode = mode
                ).collect { accumulated ->
                    latestText = accumulated
                    _streamingResponse.value = accumulated
                }

                if (latestText.isNotBlank()) {
                    val saveAiResult = firestoreRepository.addChatMessage(
                        userId = currentUserId,
                        role = "model",
                        text = latestText,
                        modelUsed = mode.labelFa
                    )
                    if (saveAiResult.isFailure) {
                        _errorMessage.value = "پاسخ تولید شد اما در بایگانی گفتگو ذخیره نشد."
                    }
                } else {
                    _errorMessage.value = "پاسخی از هوش مصنوعی دریافت نشد."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Streaming chat timed out", e)
                _errorMessage.value = "زمان پاسخ‌گویی به پایان رسید (۶۰ ثانیه). لطفاً دوباره تلاش کنید."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                _errorMessage.value = e.localizedMessage ?: "خطا در ارتباط با هوش مصنوعی رخ داد."
            } finally {
                _streamingResponse.value = null
                _isGenerating.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            val result = firestoreRepository.clearChatHistory(currentUserId)
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "پاک‌سازی گفتگو انجام نشد."
            }
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
