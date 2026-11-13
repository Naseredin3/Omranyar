package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ChatMessage
import com.example.ui.components.AttachmentPreviewChip
import com.example.ui.components.ModelModeSelector
import com.example.ui.viewmodel.ChatHistoryState
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.loadDocumentAttachment
import com.example.util.loadImageAttachment
import kotlinx.coroutines.launch

private val StarterPrompts = listOf(
    "نکات کلیدی کنترل کیفیت بتن‌ریزی طبق نشریه ۵۵ چیست؟",
    "ساختار صورت‌وضعیت موقت پیمانکاری و کسورات قانونی را توضیح بده.",
    "چک‌لیست نظارت بر روکش آسفالت معابر شهری اصفهان را بنویس.",
    "پیش‌نویس صورت‌جلسه تحویل زمین کارگاه عمرانی شهرداری را تنظیم کن."
)

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val streamingResponse by viewModel.streamingResponse.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    var localAttachmentError by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = loadImageAttachment(context, uri)
                res.onSuccess {
                    localAttachmentError = null
                    viewModel.attachInput(it)
                }.onFailure {
                    localAttachmentError = it.localizedMessage
                }
            }
        }
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = loadDocumentAttachment(context, uri)
                res.onSuccess {
                    localAttachmentError = null
                    viewModel.attachInput(it)
                }.onFailure {
                    localAttachmentError = it.localizedMessage
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val messages = (messagesState as? ChatHistoryState.Success)?.messages.orEmpty()

    LaunchedEffect(messages.size, streamingResponse) {
        val total = messages.size + if (streamingResponse != null) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top utility strip: Model mode + Clear button
        Row(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModelModeSelector(
                selectedMode = selectedMode,
                onSelectMode = viewModel::setAiMode
            )
            if (messages.isNotEmpty() && !isGenerating) {
                TextButton(
                    onClick = viewModel::clearChatHistory,
                    modifier = Modifier.testTag("clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("گفتگوی جدید", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Chat Feed
        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 840.dp)
                .fillMaxWidth()
        ) {
            when (val state = messagesState) {
                is ChatHistoryState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ChatHistoryState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is ChatHistoryState.Success -> {
                    if (state.messages.isEmpty() && streamingResponse == null) {
                        ChatEmptyWelcome(
                            onPromptSelected = { prompt ->
                                viewModel.sendMessage(prompt)
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.messages, key = { it.id }) { msg ->
                                ChatBubble(message = msg)
                            }
                            val liveStream = streamingResponse
                            if (liveStream != null) {
                                item(key = "streaming_bubble") {
                                    ChatBubble(
                                        message = ChatMessage(
                                            id = "streaming",
                                            role = "model",
                                            text = liveStream.ifBlank { "در حال تحلیل مهندسی..." },
                                            modelUsed = selectedMode.labelFa
                                        ),
                                        isStreaming = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Error banners
        val activeError = errorMessage ?: localAttachmentError
        if (activeError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        localAttachmentError = null
                        viewModel.dismissError()
                    }) {
                        Text("بستن")
                    }
                }
            }
        }

        // Composer area
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                pendingAttachment?.let { attachment ->
                    AttachmentPreviewChip(
                        attachment = attachment,
                        onClear = viewModel::clearAttachment,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isGenerating,
                        modifier = Modifier.testTag("chat_attach_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = "پیوست تصویر فنی",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = {
                            docPicker.launch(arrayOf("application/pdf", "text/*"))
                        },
                        enabled = !isGenerating,
                        modifier = Modifier.testTag("chat_attach_doc_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = "پیوست فایل PDF یا متنی",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = "پرسش فنی، مقررات یا گزارش خود را بنویسید...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )

                    IconButton(
                        onClick = {
                            val prompt = inputText
                            inputText = ""
                            viewModel.sendMessage(prompt)
                        },
                        enabled = !isGenerating && (inputText.isNotBlank() || pendingAttachment != null),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_send_button")
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "ارسال پیام"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatEmptyWelcome(
    onPromptSelected: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.height(145.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_isfahan_hero),
                        contentDescription = "سربرگ سازمان عمران شهرداری اصفهان",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.15f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "دستیار تخصصی عمران‌یار",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "آماده پاسخ‌گویی به پرسش‌های فنی، ضوابط اجرایی، فهرست بها و بررسی تصاویر کارگاهی",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "پیشنهادهای شروع سریع",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(StarterPrompts) { prompt ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .clickable { onPromptSelected(prompt) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isStreaming: Boolean = false
) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 4.dp else 18.dp,
                bottomEnd = if (isUser) 18.dp else 4.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            },
            tonalElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier
                .widthIn(max = 620.dp)
                .then(
                    if (!isUser) {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                        )
                    } else Modifier
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isStreaming) "عمران‌یار (در حال نگارش...)" else "عمران‌یار${if (message.modelUsed.isNotBlank()) " • ${message.modelUsed}" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (message.attachmentName.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isUser) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "پیوست: ${message.attachmentName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
