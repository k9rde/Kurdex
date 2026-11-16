package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.AppUpdateInfo
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.util.AppUpdateChecker

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit,
    onOpenTelegram: () -> Unit,
    onStartDirectDownload: () -> Unit,
    onInstallDownloadedApk: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (!updateInfo.isMandatory && !updateInfo.isDownloading) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.isMandatory && !updateInfo.isDownloading,
            dismissOnClickOutside = !updateInfo.isMandatory && !updateInfo.isDownloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
                .testTag("app_update_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF071912)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(GoldAccent.copy(alpha = 0.85f), EmeraldLight.copy(alpha = 0.45f))
                )
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top close button (hidden while actively downloading to prevent accidental cancellation)
                if (!updateInfo.isMandatory && !updateInfo.isDownloading) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(32.dp)
                                .testTag("update_dialog_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color(0x99FFFFFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Header Animated Icon (switches to checkmark when 100% complete)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = if (updateInfo.isDownloadComplete) {
                                    listOf(EmeraldLight.copy(alpha = 0.4f), Color(0x10135D48))
                                } else {
                                    listOf(GoldAccent.copy(alpha = 0.35f), Color(0x10135D48))
                                }
                            )
                        )
                        .border(
                            1.5.dp,
                            if (updateInfo.isDownloadComplete) EmeraldLight else GoldAccent,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (updateInfo.isDownloadComplete) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldLight,
                            modifier = Modifier.size(38.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = GoldLight,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Version Badge Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = EmeraldPrimary.copy(alpha = 0.3f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldLight.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NewReleases,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (updateInfo.isDownloadComplete) "التحديث جاهز للتثبيت!" else "إصدار جديد متوفر v${updateInfo.latestVersionName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Main Title
                Text(
                    text = if (updateInfo.isDownloadComplete) "اكتمل التنزيل بنجاح" else "قم بالتحديث الآن",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (updateInfo.isDownloadComplete) {
                        "اضغط على زر التثبيت لتطبيق الإصدار الجديد فوراً"
                    } else {
                        "تمت إضافة ميزات وتحسينات جديدة على صلاتي وأذكاري"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xCCFFFFFF),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Version comparison pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "الإصدار الحالي",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0x99FFFFFF)
                        )
                        Text(
                            text = "v${updateInfo.currentVersionName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "➜",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "الإصدار الجديد",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0x99FFFFFF)
                        )
                        Text(
                            text = "v${updateInfo.latestVersionName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldLight
                        )
                    }
                }

                // Release notes (hidden when downloading to keep focus on progress)
                if (!updateInfo.isDownloading && updateInfo.releaseNotesAr.isNotBlank()) {
                    val noteItems = AppUpdateChecker.extractReleaseNoteItems(updateInfo.releaseNotesAr)
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 185.dp)
                            .testTag("update_release_notes_card"),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x28000000),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "✨ ما تم إضافته في الإصدار v${updateInfo.latestVersionName}:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                            noteItems.forEach { (emoji, featureText) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x16FFFFFF))
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = emoji,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = featureText,
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                        color = Color(0xF0FFFFFF),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- IN-APP DIRECT DOWNLOAD PROGRESS VIEW (0% to 100%) ---
                if (updateInfo.isDownloading) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = updateInfo.downloadProgress,
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                        label = "download_progress"
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("in_app_download_progress_card"),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0x2A135D48),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldLight.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        tint = GoldAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "جارٍ التحميل المباشر...",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Text(
                                    text = "${updateInfo.downloadPercentage}%",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldLight,
                                    modifier = Modifier.testTag("download_percentage_text")
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Smooth 0% - 100% Progress Bar
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = GoldAccent,
                                trackColor = Color(0x33FFFFFF)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = updateInfo.downloadProgressText.ifBlank { "جارٍ استلام حزمة التحديث..." },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xCCFFFFFF)
                                )
                                Text(
                                    text = "0% ➜ 100%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldLight.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "يتم التنزيل من داخل التطبيق دون فتح أي متصفح، وسيُطلب التثبيت تلقائياً.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0x99FFFFFF),
                        textAlign = TextAlign.Center
                    )
                }

                // Error state during download
                if (updateInfo.downloadErrorMessage != null && !updateInfo.isDownloading) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x33D32F2F),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = updateInfo.downloadErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onStartDirectDownload,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldPrimary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إعادة المحاولة الآن")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // --- ACTION BUTTONS (WHEN NOT ACTIVELY DOWNLOADING) ---
                if (!updateInfo.isDownloading) {
                    // IF DOWNLOAD COMPLETED: SHOW INSTALL BUTTON
                    if (updateInfo.isDownloadComplete) {
                        Button(
                            onClick = onInstallDownloadedApk,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .testTag("install_update_apk_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InstallMobile,
                                    contentDescription = null,
                                    tint = GoldLight,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "تثبيت التحديث الآن 🚀",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Re-download button option
                        OutlinedButton(
                            onClick = onStartDirectDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF))
                        ) {
                            Text(
                                text = "إعادة تحميل ملف التحديث (APK)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xCCFFFFFF)
                            )
                        }
                    } else {
                        // INITIAL STATE: DIRECT DOWNLOAD BUTTON & TELEGRAM BUTTON
                        Button(
                            onClick = onStartDirectDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 50.dp)
                                .testTag("start_in_app_download_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = GoldLight,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تحميل مباشر من داخل التطبيق (APK)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Telegram Channel Update Button
                        OutlinedButton(
                            onClick = onOpenTelegram,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("update_telegram_button"),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2AABEE)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF2AABEE).copy(alpha = 0.12f)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_social_telegram),
                                    contentDescription = null,
                                    tint = Color(0xFF2AABEE),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "تحديث عبر التليكرام (\u2066@AS3SA\u2069)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2AABEE)
                                )
                            }
                        }
                    }

                    // Later / Dismiss Button
                    if (!updateInfo.isMandatory) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_dismiss_button")
                        ) {
                            Text(
                                text = "تذكيري لاحقاً",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0x99FFFFFF)
                            )
                        }
                    }
                }
            }
        }
    }
}
