package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AttachmentPreviewChip
import com.example.ui.components.ModelModeSelector
import com.example.ui.components.StructuredReportCard
import com.example.ui.theme.StatusApprovedBg
import com.example.ui.theme.StatusApprovedGreen
import com.example.ui.viewmodel.AnalysisUiState
import com.example.ui.viewmodel.AnalyzerViewModel
import com.example.util.loadDocumentAttachment
import com.example.util.loadImageAttachment
import kotlinx.coroutines.launch

private val SampleReports = listOf(
    "گزارش بتن‌ریزی پل" to """
        گزارش روزانه نظارت کارگاهی - پروژه تقاطع غیرهمسطح شهید خرازی (منطقه ۲ اصفهان)
        مورخ ۱۴۰۵/۰۸/۲۰ - عملیات بتن‌ریزی کوله شرقی با عیار ۴۰۰ و رده C35 به حجم ۱۳۵ مترمکعب انجام شد.
        اسلامپ اندازه‌گیری‌شده در محل ۹ سانتی‌متر و دمای بتن ۲۴ درجه سانتی‌گراد ثبت گردید. ۶ نمونه مکعبی جهت آزمایش ۷ و ۲۸ روزه اخذ شد.
        تذکر ایمنی: حفاظ لبه گود در ضلع شمالی نیازمند تقویت فوری پیش از شروع آرماتوربندی تیرها می‌باشد.
    """.trimIndent(),
    "صورت‌جلسه آسفالت معبر" to """
        صورت‌جلسه کارگاهی شماره ۴۲ - معاونت فنی و عمرانی منطقه ۵ شهرداری اصفهان
        موضوع: روکش آسفالت مکانیزه خیابان هزارجریب (باند غربی، طول ۸۵۰ متر)
        در حضور نماینده پیمانکار، مشاور و ناظر مقیم سازمان عمران، عملیات تراش لایه فرسوده، پاشش قیر تک‌کت و پخش آسفالت بیندر با ضخامت ۶ سانتی‌متر بررسی شد.
        مغزه‌گیری تراکم در ۴ مقطع انجام و جهت کنترل درصد قیر و دانه‌بندی به آزمایشگاه مکانیک خاک ارسال گردید. تحویل موقت مشروط به تاییدیه آزمایشگاه است.
    """.trimIndent(),
    "نامه‌نگاری صورت‌وضعیت" to """
        به: مدیریت محترم امور پیمان‌ها و رسیدگی سازمان عمران شهرداری اصفهان
        موضوع: ارسال صورت‌وضعیت موقت شماره ۶ پروژه بهسازی جداره بافت تاریخی جلفا
        با سلام؛ به پیوست صورت‌وضعیت موقت شماره ۶ شامل احجام سنگ‌فرش پیاده‌راه، مرمت جداره آجری و تاسیسات روشنایی به مبلغ کل ۴۲ میلیارد ریال مطابق فهرست بهای ابنیه و راه ارسال می‌گردد.
        با توجه به تغییر نقشه کانال جمع‌آوری آب‌های سطحی در کوچه سوم، صدور دستورکار اصلاحی برای ردیف‌های ستاره‌دار الزامی است.
    """.trimIndent()
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyzerScreen(
    viewModel: AnalyzerViewModel,
    onOpenArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val attachment by viewModel.attachment.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var textInput by rememberSaveable { mutableStateOf("") }
    var fileLoadError by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                loadImageAttachment(context, uri)
                    .onSuccess {
                        fileLoadError = null
                        viewModel.setAttachment(it)
                    }
                    .onFailure { fileLoadError = it.localizedMessage }
            }
        }
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                loadDocumentAttachment(context, uri)
                    .onSuccess {
                        fileLoadError = null
                        viewModel.setAttachment(it)
                    }
                    .onFailure { fileLoadError = it.localizedMessage }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 840.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "تحلیل و دسته‌بندی هوشمند مستندات",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "متن گزارش عمرانی، تصویر نقشه/کارگاه یا فایل PDF را وارد کنید تا هوش مصنوعی آن را خلاصه‌سازی، ریسک‌یابی و در بایگانی دسته‌بندی کند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ModelModeSelector(
                        selectedMode = selectedMode,
                        onSelectMode = viewModel::setAiMode
                    )
                }
            }

            // Sample quick fill templates
            Column {
                Text(
                    text = "نمونه گزارش‌های آماده برای آزمایش سریع:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SampleReports.forEach { (label, body) ->
                        AssistChip(
                            onClick = {
                                textInput = body
                                viewModel.resetResult()
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // Multimodal Attachment Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = uiState !is AnalysisUiState.Analyzing,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("analyzer_pick_image_button")
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تصویر کارگاه / نامه", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = {
                        docPicker.launch(arrayOf("application/pdf", "text/*"))
                    },
                    enabled = uiState !is AnalysisUiState.Analyzing,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("analyzer_pick_file_button")
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("فایل PDF یا متنی", style = MaterialTheme.typography.labelLarge)
                }
            }

            attachment?.let { att ->
                AttachmentPreviewChip(
                    attachment = att,
                    onClear = viewModel::clearAttachment
                )
            }

            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text("متن گزارش عمرانی، صورت‌جلسه یا شرح فنی") },
                placeholder = { Text("متن سند را اینجا بنویسید یا پیوست اضافه کنید...") },
                minLines = 5,
                maxLines = 10,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analyzer_text_input")
            )

            Button(
                onClick = { viewModel.analyzeAndArchive(textInput) },
                enabled = uiState !is AnalysisUiState.Analyzing && (textInput.isNotBlank() || attachment != null),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("analyze_submit_button")
            ) {
                if (uiState is AnalysisUiState.Analyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("در حال تحلیل و دسته‌بندی هوشمند...", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.Filled.Analytics, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تحلیل هوشمند و ذخیره در بایگانی", style = MaterialTheme.typography.titleMedium)
                }
            }

            val err = fileLoadError ?: (uiState as? AnalysisUiState.Error)?.message
            if (err != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            if (uiState is AnalysisUiState.Success) {
                val analyzedDoc = (uiState as AnalysisUiState.Success).document
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StatusApprovedBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = StatusApprovedGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "سند با موفقیت تحلیل و در بایگانی ذخیره شد.",
                            style = MaterialTheme.typography.labelLarge,
                            color = StatusApprovedGreen
                        )
                    }
                }

                StructuredReportCard(document = analyzedDoc)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            textInput = ""
                            viewModel.clearAttachment()
                            viewModel.resetResult()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("سند جدید")
                    }
                    Button(
                        onClick = onOpenArchive,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاهده بایگانی")
                    }
                }
            }
        }
    }
}
