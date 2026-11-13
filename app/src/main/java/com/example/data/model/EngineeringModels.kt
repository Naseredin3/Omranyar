package com.example.data.model

import android.graphics.Bitmap
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

object DocumentCategories {
    const val SITE_MINUTES = "صورت‌جلسه کارگاهی"
    const val PROGRESS_REPORT = "گزارش پیشرفت فیزیکی"
    const val TECHNICAL_SPEC = "مستندات فنی و اجرایی"
    const val FINANCIAL_STATEMENT = "صورت‌وضعیت و امور مالی"
    const val QUALITY_CONTROL = "کنترل کیفیت و آزمایشگاه"
    const val OFFICIAL_LETTER = "نامه اداری و مکاتبات"
    const val HSE_SAFETY = "ایمنی و HSE"
    const val OTHER = "سایر"

    val all: List<String> = listOf(
        PROGRESS_REPORT,
        TECHNICAL_SPEC,
        FINANCIAL_STATEMENT,
        QUALITY_CONTROL,
        SITE_MINUTES,
        HSE_SAFETY,
        OFFICIAL_LETTER,
        OTHER
    )
}

object DocumentPriorities {
    const val URGENT = "فوری"
    const val HIGH = "بالا"
    const val NORMAL = "عادی"

    val all: List<String> = listOf(URGENT, HIGH, NORMAL)
}

object DocumentStatuses {
    const val UNDER_REVIEW = "در دست بررسی"
    const val APPROVED = "تأیید شده"
    const val NEEDS_REVISION = "نیازمند اصلاح"

    val all: List<String> = listOf(UNDER_REVIEW, APPROVED, NEEDS_REVISION)
}

object SourceTypes {
    const val TEXT = "متن"
    const val IMAGE = "تصویر"
    const val FILE = "فایل"
}

enum class AiModelMode(val modelName: String, val labelFa: String, val subtitleFa: String) {
    PRECISION(
        modelName = "gemini-3.8-flash",
        labelFa = "دقت بالا",
        subtitleFa = "تحلیل دقیق فنی و مقررات عمرانی"
    ),
    FAST(
        modelName = "gemini-3.5-flash-lite",
        labelFa = "سرعت بالا",
        subtitleFa = "پاسخ‌گویی آنی و دسته‌بندی سریع"
    )
}

@IgnoreExtraProperties
data class TechnicalDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = DocumentCategories.OTHER,
    val priority: String = DocumentPriorities.NORMAL,
    val status: String = DocumentStatuses.UNDER_REVIEW,
    val projectArea: String = "عمومی سازمان عمران",
    val sourceType: String = SourceTypes.TEXT,
    val originalExcerpt: String = "",
    val summary: String = "",
    val keyFindings: List<String> = emptyList(),
    val actionItems: List<String> = emptyList(),
    val risksOrIssues: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val text: String = "",
    val attachmentName: String = "",
    val modelUsed: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed class AttachmentInput {
    abstract val displayName: String

    data class ImageAttachment(
        override val displayName: String,
        val bitmap: Bitmap
    ) : AttachmentInput()

    data class DocumentFileAttachment(
        override val displayName: String,
        val mimeType: String,
        val bytes: ByteArray,
        val extractedText: String? = null
    ) : AttachmentInput()
}

data class DocumentAnalysisResult(
    val title: String,
    val category: String,
    val priority: String,
    val projectArea: String,
    val summary: String,
    val keyFindings: List<String>,
    val actionItems: List<String>,
    val risksOrIssues: List<String>,
    val tags: List<String>
)
