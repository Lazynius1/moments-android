package com.moments.android.reportes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.models.Story
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Port de ModernReportContent.swift */
@Composable
fun ModernReportContent(
    moment: Moment?,
    story: Story?,
    reportedUserId: String?,
    reportedUsername: String?,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val persistence = remember { LocalPersistenceService }
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black.copy(alpha = 0.9f)
    val secondaryText = if (isDark) Color.White.copy(alpha = 0.64f) else Color.Black.copy(alpha = 0.58f)

    var selectedCategory by remember { mutableStateOf<ReportCategory?>(null) }
    var additionalDetails by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    val contentTypeLabel = when {
        moment != null -> stringResource(R.string.report_contentType_moment)
        story != null -> stringResource(R.string.report_contentType_story)
        else -> stringResource(R.string.report_contentType_user)
    }
    val contentId = moment?.id ?: story?.id ?: reportedUserId.orEmpty()
    val authorId = moment?.authorId ?: story?.authorId ?: reportedUserId.orEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp),
    ) {
        NativeReportSheetHeader(
            title = stringResource(
                R.string.report_title,
                contentTypeLabel.replaceFirstChar { it.uppercase() },
            ),
            onBack = onBack,
        )

        if (showSuccessMessage) {
            ReportSuccessView(
                primaryText = primaryText,
                secondaryText = secondaryText,
                onDismiss = onDismiss,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = if (selectedCategory != null) 120.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text(
                        stringResource(R.string.report_subtitle, contentTypeLabel),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = secondaryText,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Start,
                    )
                    NativeReportOptionsSection {
                        ReportCategory.allCases.forEachIndexed { index, category ->
                            NativeReportOptionRow(
                                icon = category.icon,
                                title = stringResource(category.titleRes),
                                subtitle = stringResource(category.subtitleRes),
                                isSelected = selectedCategory == category,
                                showsChevron = selectedCategory != category,
                                onClick = { selectedCategory = category },
                            )
                            if (index < ReportCategory.allCases.lastIndex) NativeReportDivider()
                        }
                    }
                    AnimatedVisibility(
                        visible = selectedCategory != null,
                        enter = slideInVertically { it / 4 } + fadeIn(),
                        exit = fadeOut(),
                    ) {
                        NativeReportDetailsSection(
                            title = stringResource(R.string.report_additionalDetails),
                            placeholder = stringResource(R.string.report_detailsPlaceholder),
                            text = additionalDetails,
                            onTextChange = { additionalDetails = it },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }

                if (selectedCategory != null) {
                    NativeReportSubmitBar(
                        isSubmitting = isSubmitting,
                        title = stringResource(R.string.report_sendButton),
                        containerColor = colors.surfaceBackground,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onClick = {
                            val category = selectedCategory ?: return@NativeReportSubmitBar
                            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                                ?: return@NativeReportSubmitBar
                            if (contentId.isEmpty()) return@NativeReportSubmitBar
                            isSubmitting = true
                            val reportedContentType = when {
                                moment != null -> "moment"
                                story != null -> "story"
                                else -> "user"
                            }
                            scope.launch {
                                persistence.reportContent(
                                    reporterId = currentUserId,
                                    reportedUserId = authorId,
                                    reportedContentType = reportedContentType,
                                    reportedContentId = contentId,
                                    category = category.raw,
                                    description = additionalDetails.trim(),
                                    priority = category.priority.raw,
                                )
                                isSubmitting = false
                                showSuccessMessage = true
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun NativeReportSheetHeader(title: String, onBack: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black.copy(alpha = 0.9f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 0.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = primaryText,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = primaryText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(44.dp))
    }
}

@Composable
fun NativeReportOptionsSection(content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) { content() }
}

@Composable
fun NativeReportOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    showsChevron: Boolean,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black.copy(alpha = 0.9f)
    val secondaryText = if (isDark) Color.White.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.55f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = primaryText,
            modifier = Modifier.width(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = secondaryText, fontSize = 13.sp)
            }
        }
        when {
            isSelected -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(16.dp),
            )
            showsChevron -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = secondaryText,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun NativeReportDivider() {
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val hairline = with(density) { (1f / density.density).toDp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp)
            .height(hairline)
            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)),
    )
}

@Composable
fun NativeReportDetailsSection(
    title: String,
    placeholder: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black.copy(alpha = 0.9f)
    val placeholderColor = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.28f)
    val fieldBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = primaryText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(placeholder, color = placeholderColor, fontSize = 15.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 108.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(fieldBg),
            colors = TextFieldDefaults.colors(
                focusedTextColor = primaryText,
                unfocusedTextColor = primaryText,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = primaryText,
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = primaryText),
        )
    }
}

@Composable
fun NativeReportSubmitBar(
    isSubmitting: Boolean,
    title: String,
    onClick: () -> Unit,
    containerColor: Color = rememberAdaptiveColors().surfaceBackground,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(containerColor)) {
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.25f), thickness = 0.5.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Red)
                .clickable(enabled = !isSubmitting, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ReportSuccessView(
    onDismiss: () -> Unit,
    primaryText: Color = if (isSystemInDarkTheme()) Color.White else Color.Black.copy(alpha = 0.9f),
    secondaryText: Color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.64f) else Color.Black.copy(alpha = 0.58f),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        delay(2000)
        onDismiss()
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color.Green.copy(alpha = 0.1f)),
            )
            Icon(
                Icons.Default.Verified,
                contentDescription = null,
                tint = Color.Green,
                modifier = Modifier.size(50.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.report_success_title),
            color = primaryText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier.height(8.dp))
        Text(
            stringResource(R.string.report_success_message),
            color = secondaryText,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
    }
}
