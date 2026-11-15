package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.DayPrayerTimes
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight

val ARABIC_MONTHS = listOf(
    "1. كانون الثاني (يناير)",
    "2. شباط (فبراير)",
    "3. آذار (مارس)",
    "4. نيسان (أبريل)",
    "5. أيار (مايو)",
    "6. حزيران (يونيو)",
    "7. تموز (يوليو)",
    "8. آب (أغسطس)",
    "9. أيلول (سبتمبر)",
    "10. تشرين الأول (أكتوبر)",
    "11. تشرين الثاني (نوفمبر)",
    "12. كانون الأول (ديسمبر)"
)

@Composable
fun MonthlyImsakiahDialog(
    cityName: String,
    selectedMonth: Int, // 1..12
    selectedYear: Int,
    daysList: List<DayPrayerTimes>,
    currentDayNumber: Int,
    currentMonthNumber: Int,
    onMonthSelected: (Int) -> Unit,
    onDaySelected: (dayNumber: Int, monthNumber: Int, yearNumber: Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("monthly_imsakiah_dialog"),
            color = Color(0xFF071912),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = EmeraldContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "إمساكية ومواقيت أشهر السنة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$cityName • $selectedYear م",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldLight
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_imsakiah_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Month selection chips (1..12)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(ARABIC_MONTHS) { idx, monthName ->
                        val mNum = idx + 1
                        val isSelected = mNum == selectedMonth
                        FilterChip(
                            selected = isSelected,
                            onClick = { onMonthSelected(mNum) },
                            label = { Text(monthName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldAccent,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x33FFFFFF),
                                labelColor = Color.White
                            ),
                            modifier = Modifier.testTag("month_chip_$mNum")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Table Header Row
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x33000000),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("اليوم", modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center, color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("الفجر", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("الشروق", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("الظهر", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("العصر", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("المغرب", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("العشاء", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Days List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(daysList) { index, dayTimes ->
                        val dayNumber = index + 1
                        val isToday = (dayNumber == currentDayNumber && selectedMonth == currentMonthNumber)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isToday) EmeraldPrimary.copy(alpha = 0.35f) else Color(0x1AFFFFFF),
                            border = if (isToday) androidx.compose.foundation.BorderStroke(1.5.dp, GoldAccent) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDaySelected(dayNumber, selectedMonth, selectedYear)
                                    onDismiss()
                                }
                                .testTag("imsakiah_day_$dayNumber")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isToday) "$dayNumber (اليوم)" else "$dayNumber",
                                    modifier = Modifier.weight(1.1f),
                                    textAlign = TextAlign.Center,
                                    color = if (isToday) GoldAccent else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold
                                )
                                Text(cleanAmPm(dayTimes.fajr), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.White, fontSize = 11.sp)
                                Text(cleanAmPm(dayTimes.sunrise), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color(0xCCFFFFFF), fontSize = 11.sp)
                                Text(cleanAmPm(dayTimes.dhuhr), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.White, fontSize = 11.sp)
                                Text(cleanAmPm(dayTimes.asr), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.White, fontSize = 11.sp)
                                Text(cleanAmPm(dayTimes.maghrib), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = if (isToday) GoldAccent else Color.White, fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                                Text(cleanAmPm(dayTimes.isha), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom note
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x22000000),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "اضغط على أي يوم لاختياره مباشرة وعرض مواقيت صلواته • الحساب مبني على التقويم الفلكي الدقيق",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xCCFFFFFF),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

private fun cleanAmPm(timeStr: String): String {
    return timeStr.replace(" ص", "").replace(" م", "").replace("ص", "").replace("م", "").trim()
}
