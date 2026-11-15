package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.QuranWirdData
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.util.ShareCardGenerator
import java.util.Calendar

@Composable
fun QuranWirdDialog(
    lastReadSurahId: String,
    fontSizeSp: Float,
    isTodayWirdCompleted: Boolean,
    totalWirdsCount: Int,
    cityAndDateLabel: String,
    onSelectSurahBookmark: (String) -> Unit,
    onAdjustFontSize: (Float) -> Unit,
    onToggleWirdCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isFriday = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY }

    var selectedSurahId by remember(lastReadSurahId) {
        mutableStateOf(
            if (QuranWirdData.surahs.any { it.id == lastReadSurahId }) lastReadSurahId
            else if (isFriday) "kahf_wird"
            else QuranWirdData.surahs.first().id
        )
    }

    val currentSurah = remember(selectedSurahId) {
        QuranWirdData.surahs.find { it.id == selectedSurahId } ?: QuranWirdData.surahs.first()
    }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(26.dp))
                .testTag("quran_wird_dialog"),
            color = Color(0xFF061711),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EmeraldPrimary.copy(alpha = 0.35f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.padding(9.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "الورد القرآني والتلاوة اليومية",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "إجمالي الأوراد المكتملة: $totalWirdsCount ورد",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldLight
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_quran_wird_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color.White
                        )
                    }
                }

                // Friday Kahf Special Reminder Banner
                if (isFriday) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSurahId = "kahf_wird" },
                        shape = RoundedCornerShape(14.dp),
                        color = GoldAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = GoldLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "جمعة مباركة! لا تنسَ قراءة سورة الكهف اليوم",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                            }
                            if (selectedSurahId != "kahf_wird") {
                                Text(
                                    text = "افتح الكهف",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldLight
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Surah Selection Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(QuranWirdData.surahs, key = { it.id }) { surah ->
                        val isSelected = surah.id == selectedSurahId
                        val isBookmarked = surah.id == lastReadSurahId
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSurahId = surah.id },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = surah.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (surah.isFridayRecommended) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "✨", fontSize = 12.sp)
                                    }
                                    if (isBookmarked) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = "آخر قراءة",
                                            modifier = Modifier.size(14.dp),
                                            tint = if (isSelected) Color.White else GoldAccent
                                        )
                                    }
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color(0xEEFFFFFF)
                            ),
                            modifier = Modifier.testTag("surah_chip_${surah.id}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Font Size & Bookmark Quick Controls Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x1EFFFFFF))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "حجم الخط:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xCCFFFFFF)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = { onAdjustFontSize(-2f) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text("A-", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(
                            text = "${fontSizeSp.toInt()}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        FilledTonalIconButton(
                            onClick = { onAdjustFontSize(2f) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text("A+", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    val isCurrentBookmarked = selectedSurahId == lastReadSurahId
                    TextButton(
                        onClick = { onSelectSurahBookmark(currentSurah.id) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "حفظ الموضع",
                            tint = if (isCurrentBookmarked) GoldAccent else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCurrentBookmarked) "محفوظ كآخر موضع" else "حفظ علامة القراءة",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrentBookmarked) GoldLight else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Surah Content Scroll Area
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x280D3224),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp)
                    ) {
                        Text(
                            text = currentSurah.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = GoldLight,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = currentSurah.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xAAFFFFFF),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = GoldAccent.copy(alpha = 0.25f))
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = currentSurah.versesText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.72f).sp
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFFAEB),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = EmeraldPrimary.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, EmeraldLight.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "✨ فضلها وبركتها:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentSurah.virtue,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xDDFFFFFF)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Row: Mark Daily Wird Completed + Share Story Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleWirdCompleted,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("complete_wird_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTodayWirdCompleted) GoldAccent else EmeraldPrimary,
                            contentColor = if (isTodayWirdCompleted) Color.Black else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isTodayWirdCompleted) Icons.Default.CheckCircle else Icons.Default.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTodayWirdCompleted) "تم إتمام ورد اليوم ✓" else "تحديد ورد اليوم كمكتمل",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val rawText = currentSurah.versesText.trim()
                            val excerpt = if (rawText.length <= 460) {
                                rawText
                            } else {
                                val candidate = rawText.substring(0, 460)
                                val lastVerseEnd = candidate.lastIndexOf('﴾')
                                val lastSpace = candidate.lastIndexOf(' ')
                                val cutIndex = when {
                                    lastVerseEnd > 140 -> lastVerseEnd + 1
                                    lastSpace > 140 -> lastSpace
                                    else -> 460
                                }
                                rawText.substring(0, cutIndex).trim() +
                                    if (lastVerseEnd > 140) "" else "..."
                            }
                            ShareCardGenerator.shareStoryCard(
                                context = context,
                                badgeLabel = "الورد القرآني اليومي",
                                title = currentSurah.title,
                                arabicBody = excerpt,
                                source = currentSurah.subtitle,
                                cityAndDate = cityAndDateLabel
                            )
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("share_quran_story_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = GoldLight
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "مشاركة للستوري",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ستوري", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
