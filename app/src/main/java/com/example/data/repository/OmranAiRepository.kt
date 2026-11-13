package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiModelMode
import com.example.data.model.AttachmentInput
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentAnalysisResult
import com.example.data.model.DocumentCategories
import com.example.data.model.DocumentPriorities
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class OmranAiRepository {

    private val systemPrompt = """
        شما «عمران‌یار»، دستیار هوش مصنوعی تخصصی و بومی‌سازی‌شده برای سازمان عمران شهرداری اصفهان هستید.
        حوزه تخصصی شما شامل مدیریت پروژه‌های عمرانی شهری، مقررات ملی ساختمان (مباحث ۲۲گانه)، نشریات فنی سازمان برنامه و بودجه (از جمله نشریه ۵۵ و نشریه ۱۰۱)، فهرست بهای ابنیه و راه و باند، آیین‌نامه ۲۸۰۰، صورت‌وضعیت‌نویسی و تعدیل، کنترل کیفیت مصالح (بتن، آسفالت، میلگرد)، ایمنی کارگاه (HSE)، و ضوابط اجرایی معابر و تقاطع‌های غیرهمسطح و طرح‌های شهری اصفهان است.
        اصول پاسخ‌گویی:
        ۱. همیشه به زبان فارسی رسمی، دقیق، مهندسی و شفاف پاسخ دهید.
        ۲. در تحلیل فنی، ساختارمند (تیتر، بولت‌پوینت، گام‌های اجرایی و ریسک‌ها) بنویسید.
        ۳. از ارائه اعداد یا مواد قانونی جعلی خودداری کنید و در صورت ابهام در داده‌های ورودی، موارد نیازمند بررسی میدانی یا آزمایشگاهی را صراحتاً مشخص کنید.
    """.trimIndent()

    private val chatPrecisionModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelMode.PRECISION.modelName,
            systemInstruction = content { text(systemPrompt) },
            generationConfig = generationConfig {
                temperature = 0.35f
            }
        )
    }

    private val chatFastModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelMode.FAST.modelName,
            systemInstruction = content { text(systemPrompt) },
            generationConfig = generationConfig {
                temperature = 0.3f
            }
        )
    }

    private val analysisSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "عنوان دقیق و حرفه‌ای فارسی برای سند یا گزارش عمرانی (حداکثر ۱۲۰ کاراکتر)"),
                "category" to Schema.enumeration(
                    values = DocumentCategories.all,
                    description = "مناسب‌ترین دسته‌بندی سازمانی برای این سند"
                ),
                "priority" to Schema.enumeration(
                    values = DocumentPriorities.all,
                    description = "سطح اولویت و فوریت رسیدگی"
                ),
                "projectArea" to Schema.string(description = "منطقه شهرداری اصفهان یا بخش فنی مرتبط (مثلاً منطقه ۳، معاونت فنی، تقاطع‌های غیرهمسطح، عمومی)"),
                "summary" to Schema.string(description = "خلاصه مدیریتی دقیق در ۲ تا ۴ جمله به فارسی"),
                "keyFindings" to Schema.array(
                    items = Schema.string(),
                    description = "حداکثر ۶ یافته یا نکته فنی کلیدی استخراج‌شده از سند"
                ),
                "actionItems" to Schema.array(
                    items = Schema.string(),
                    description = "حداکثر ۶ اقدام پیشنهادی یا دستور پیگیری برای تیم نظارت و اجرا"
                ),
                "risksOrIssues" to Schema.array(
                    items = Schema.string(),
                    description = "حداکثر ۵ ریسک فنی، ایمنی، زمانی یا قراردادی شناسایی‌شده"
                ),
                "tags" to Schema.array(
                    items = Schema.string(),
                    description = "حداکثر ۶ برچسب کلیدی فارسی برای جستجو و بایگانی"
                )
            )
        )
    }

    private val analyzerPrecisionModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelMode.PRECISION.modelName,
            systemInstruction = content { text(systemPrompt) },
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
                responseSchema = analysisSchema
            }
        )
    }

    private val analyzerFastModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelMode.FAST.modelName,
            systemInstruction = content { text(systemPrompt) },
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
                responseSchema = analysisSchema
            }
        )
    }

    fun streamChatResponse(
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: AttachmentInput?,
        mode: AiModelMode
    ): Flow<String> = flow {
        val model = when (mode) {
            AiModelMode.PRECISION -> chatPrecisionModel
            AiModelMode.FAST -> chatFastModel
        }

        val chatHistory: List<Content> = history
            .takeLast(12)
            .filter { it.text.isNotBlank() }
            .map { msg ->
                val role = if (msg.role == "model") "model" else "user"
                content(role) { text(msg.text) }
            }

        val userContent = buildInputContent(
            prompt = userPrompt.ifBlank { "لطفاً پیوست ارسالی را از دیدگاه مهندسی عمران شهرداری اصفهان تحلیل و بررسی کنید." },
            attachment = attachment
        )

        val chat = model.startChat(history = chatHistory)
        val accumulated = StringBuilder()

        withTimeout(AI_TIMEOUT_MS) {
            chat.sendMessageStream(userContent).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    emit(accumulated.toString())
                }
            }
        }
    }

    suspend fun analyzeDocument(
        textInput: String,
        attachment: AttachmentInput?,
        mode: AiModelMode
    ): DocumentAnalysisResult {
        val model = when (mode) {
            AiModelMode.PRECISION -> analyzerPrecisionModel
            AiModelMode.FAST -> analyzerFastModel
        }

        val instruction = buildString {
            appendLine("سند یا گزارش عمرانی زیر را برای سازمان عمران شهرداری اصفهان تحلیل، خلاصه‌سازی و دسته‌بندی کن.")
            appendLine("تمام خروجی‌ها را به زبان فارسی دقیق و مهندسی تولید کن.")
            if (textInput.isNotBlank()) {
                appendLine()
                appendLine("متن ورودی:")
                appendLine(textInput.trim())
            }
        }

        val inputContent = buildInputContent(instruction, attachment)

        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(inputContent)
        }

        val jsonText = response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("مدل هوش مصنوعی پاسخی برنگرداند.")

        return parseAnalysisJson(jsonText)
    }

    private fun buildInputContent(prompt: String, attachment: AttachmentInput?): Content {
        return content {
            when (attachment) {
                is AttachmentInput.ImageAttachment -> {
                    val safeBitmap = downscaleBitmapIfNeeded(attachment.bitmap, maxDimension = 1600)
                    image(safeBitmap)
                }
                is AttachmentInput.DocumentFileAttachment -> {
                    if (!attachment.extractedText.isNullOrBlank()) {
                        text("محتوای فایل پیوست (${attachment.displayName}):\n${attachment.extractedText.take(24000)}")
                    } else {
                        inlineData(attachment.bytes, attachment.mimeType)
                    }
                }
                null -> Unit
            }
            text(prompt)
        }
    }

    private fun parseAnalysisJson(jsonText: String): DocumentAnalysisResult {
        val json = JSONObject(jsonText)
        val rawCategory = json.optString("category", DocumentCategories.OTHER)
        val safeCategory = if (rawCategory in DocumentCategories.all) rawCategory else DocumentCategories.OTHER

        val rawPriority = json.optString("priority", DocumentPriorities.NORMAL)
        val safePriority = if (rawPriority in DocumentPriorities.all) rawPriority else DocumentPriorities.NORMAL

        return DocumentAnalysisResult(
            title = json.optString("title", "گزارش فنی تحلیل‌شده").ifBlank { "گزارش فنی تحلیل‌شده" },
            category = safeCategory,
            priority = safePriority,
            projectArea = json.optString("projectArea", "عمومی سازمان عمران").ifBlank { "عمومی سازمان عمران" },
            summary = json.optString("summary", "خلاصه در دسترس نیست.").ifBlank { "خلاصه در دسترس نیست." },
            keyFindings = json.optStringList("keyFindings", max = 6),
            actionItems = json.optStringList("actionItems", max = 6),
            risksOrIssues = json.optStringList("risksOrIssues", max = 5),
            tags = json.optStringList("tags", max = 6)
        )
    }

    private fun JSONObject.optStringList(key: String, max: Int): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until minOf(array.length(), max)) {
                val item = array.optString(i).trim()
                if (item.isNotEmpty()) add(item)
            }
        }
    }

    private fun downscaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } catch (e: Exception) {
            Log.w(TAG, "Bitmap downscale failed, using original", e)
            bitmap
        }
    }

    private companion object {
        const val TAG = "OmranAiRepository"
        const val AI_TIMEOUT_MS = 60_000L
    }
}
