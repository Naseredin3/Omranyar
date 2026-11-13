package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.model.AttachmentInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_INLINE_FILE_BYTES = 18 * 1024 * 1024 // 18 MB safe limit (<20MB Gemini inline limit)

suspend fun loadImageAttachment(context: Context, uri: Uri): Result<AttachmentInput.ImageAttachment> =
    withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val displayName = resolveDisplayName(context, uri) ?: "تصویر پیوست"
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = false
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(resolver, uri)
            }
            Result.success(AttachmentInput.ImageAttachment(displayName, bitmap))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("خواندن تصویر با خطا مواجه شد: ${e.localizedMessage}"))
        }
    }

suspend fun loadDocumentAttachment(context: Context, uri: Uri): Result<AttachmentInput> =
    withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val displayName = resolveDisplayName(context, uri) ?: "فایل پیوست"

            if (mimeType.startsWith("image/")) {
                return@withContext loadImageAttachment(context, uri)
            }

            val bytes = resolver.openInputStream(uri)?.use { stream ->
                val data = stream.readBytes()
                if (data.size > MAX_INLINE_FILE_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("حجم فایل بیش از ۱۸ مگابایت است. لطفاً فایل کوچک‌تری انتخاب کنید.")
                    )
                }
                data
            } ?: return@withContext Result.failure(
                IllegalStateException("امکان باز کردن فایل وجود ندارد.")
            )

            val isTextBased = mimeType.startsWith("text/") ||
                mimeType == "application/json" ||
                displayName.endsWith(".txt", ignoreCase = true) ||
                displayName.endsWith(".md", ignoreCase = true) ||
                displayName.endsWith(".csv", ignoreCase = true)

            val extractedText = if (isTextBased) {
                bytes.toString(Charsets.UTF_8)
            } else {
                null
            }

            val normalizedMime = when {
                isTextBased -> "text/plain"
                mimeType == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                else -> mimeType
            }

            if (!isTextBased && normalizedMime != "application/pdf") {
                return@withContext Result.failure(
                    IllegalArgumentException("فرمت‌های پشتیبانی‌شده شامل PDF، تصاویر فنی و فایل‌های متنی (TXT/CSV/MD) است.")
                )
            }

            Result.success(
                AttachmentInput.DocumentFileAttachment(
                    displayName = displayName,
                    mimeType = normalizedMime,
                    bytes = bytes,
                    extractedText = extractedText
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException("خطا در خواندن فایل: ${e.localizedMessage}"))
        }
    }

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    } catch (_: Exception) {
        null
    }
}
