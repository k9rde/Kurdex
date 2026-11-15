package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.PrayerItem
import com.example.model.PrayerType
import com.example.ui.components.CityPickerDialog
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MonthlyImsakiahDialog
import com.example.ui.components.QuranWirdDialog
import com.example.ui.theme.*
import com.example.util.ShareCardGenerator
import com.example.viewmodel.AppUiState
import com.example.viewmodel.AppViewModel

@Composable
fun PrayerScreen(
    viewModel: AppViewModel,
    uiState: AppUiState,
    onNavigateToDua: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            viewModel.requestGpsLocation(context)
        }
    }

    val times = uiState.prayerTimes
    val selectedCity = uiState.selectedCity

    val cityNameDisplay = if (selectedCity.nameAr.isNotEmpty()) selectedCity.nameAr else selectedCity.name
    val countryNameDisplay = if (selectedCity.countryAr.isNotEmpty()) selectedCity.countryAr else selectedCity.country

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Location and Date Header Glass Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // City & Country selector button with Liquid Glass pill
                    LiquidGlassCard(
                        cornerRadius = 24.dp,
                        onClick = { viewModel.openCityPicker(true) },
                        modifier = Modifier.testTag("city_selector_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = cityNameDisplay,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = countryNameDisplay,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xCCFFFFFF)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "تغيير الدولة والمدينة",
                                tint = Color(0xCCFFFFFF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // GPS Locate Button & Online Sync Button & Hijri Badge in Glass Pill
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiquidGlassCard(
                            cornerRadius = 20.dp,
                            onClick = {
                                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (hasFine || hasCoarse) {
                                    viewModel.requestGpsLocation(context)
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("gps_locate_button")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (uiState.isGpsLocating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = EmeraldLight
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "تحديد موقعي التلقائي عبر GPS",
                                        tint = EmeraldLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        LiquidGlassCard(
                            cornerRadius = 20.dp,
                            onClick = { viewModel.syncPrayerTimesWithOnline() },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("sync_online_button")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (uiState.isOnlineSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = GoldAccent
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (times?.isOnlineVerified == true) Icons.Default.CloudDone else Icons.Default.Sync,
                                        contentDescription = "تحديث وضبط الوقت عبر الإنترنت",
                                        tint = if (times?.isOnlineVerified == true) Color(0xFF68D391) else GoldLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Hijri Date Badge Glass
                        LiquidGlassCard(
                            cornerRadius = 20.dp
                        ) {
                            Text(
                                text = times?.hijriFormatted ?: "1448 هـ",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldLight,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // Location GPS Feedback Banner
            if (uiState.locationMessage.isNotEmpty()) {
                item {
                    LiquidGlassCard(
                        cornerRadius = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = EmeraldLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.locationMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Sync Status Banner (if active)
            if (uiState.syncStatusMessage.isNotEmpty()) {
                item {
                    LiquidGlassCard(
                        cornerRadius = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (uiState.isOnlineSynced) Icons.Default.CheckCircle else Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (uiState.isOnlineSynced) Color(0xFF68D391) else GoldLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.syncStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Day Navigation (Yesterday - Today - Tomorrow) Glass Card
            item {
                LiquidGlassCard(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.setDayOffset(uiState.dayOffset - 1) },
                                modifier = Modifier.testTag("prev_day_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "اليوم السابق",
                                    tint = GoldLight
                                )
                            }

                            val dayLabel = when (uiState.dayOffset) {
                                0 -> "اليوم • ${times?.dateFormatted ?: ""}"
                                1 -> "غداً • ${times?.dateFormatted ?: ""}"
                                -1 -> "أمس • ${times?.dateFormatted ?: ""}"
                                else -> "${if (uiState.dayOffset > 0) "+${uiState.dayOffset} يوم" else "${uiState.dayOffset} يوم"} • ${times?.dateFormatted ?: ""}"
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                if (times?.isOnlineVerified == true) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0x3368D391)
                                    ) {
                                        Text(
                                            text = "مضبوط بالإنترنت",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF9AE6B4),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.setDayOffset(uiState.dayOffset + 1) },
                                modifier = Modifier.testTag("next_day_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "اليوم التالي",
                                    tint = GoldLight
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Open 12-Month Calendar / Imsakiah Button
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openMonthlyImsakiah(true) }
                                .testTag("open_monthly_imsakiah_button"),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x24D4AF37),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x55D4AF37))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = GoldLight,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "عرض جدول مواقيت وإمساكية أشهر السنة الـ 12",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                            }
                        }
                    }
                }
            }

            // Hero Liquid Glass Countdown Card
            item {
                val nextPrayer = times?.nextPrayer
                val countdown = times?.countdownFormatted ?: "00:00:00"
                val progress = times?.progress ?: 0f

                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 210.dp)
                        .testTag("hero_countdown_card"),
                    cornerRadius = 28.dp,
                    containerBrush = Glassmorphism.dynamicAccentGlassGradient(uiState.waterOpacity)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                                    color = Color(0x33D4AF37),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier
                                            .padding(7.dp)
                                            .size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "المتبقي على أذان ${nextPrayer?.type?.titleAr ?: "الصلاة القادمة"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = nextPrayer?.type?.titleAr ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = GoldLight
                            )
                        }

                        // Giant Centered Digital Time
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = countdown,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 44.sp,
                                    letterSpacing = 2.sp
                                ),
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Next prayer time pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x33FFFFFF)
                            ) {
                                Text(
                                    text = "${nextPrayer?.type?.titleAr ?: ""}: ${nextPrayer?.timeFormatted ?: "--:--"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GoldLight,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }

                            // Iqama approximate countdown pill (active 1..20 min after Adhan)
                            if (uiState.iqamaRemainingText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = EmeraldPrimary.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, EmeraldLight.copy(alpha = 0.6f)),
                                    modifier = Modifier.testTag("iqama_countdown_pill")
                                ) {
                                    Text(
                                        text = "🕌 ${uiState.iqamaRemainingText}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Smooth progress indicator
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = GoldAccent,
                            trackColor = Color(0x33FFFFFF)
                        )
                    }
                }
            }

            // Quick Actions Row: Quran Wird, Interactive Adhkar, Nearest Mosques
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_shortcuts_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickFeaturePill(
                        title = "الورد القرآني 📖",
                        subtitle = if (uiState.isTodayWirdCompleted) "مكتمل اليوم ✓" else "تلاوة ومتابعة",
                        highlight = uiState.isTodayWirdCompleted,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_quran_wird_shortcut"),
                        onClick = { viewModel.openQuranWird(true) }
                    )
                    QuickFeaturePill(
                        title = "أذكار تفاعلية 📿",
                        subtitle = "صباح • مساء • نوم",
                        highlight = false,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_interactive_adhkar_shortcut"),
                        onClick = {
                            viewModel.setOpenInteractiveAdhkarDirect(true)
                            onNavigateToDua()
                        }
                    )
                    QuickFeaturePill(
                        title = "أقرب المساجد 🕌",
                        subtitle = "خريطة حولي",
                        highlight = false,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nearest_mosques_shortcut"),
                        onClick = {
                            val geoUri = Uri.parse("geo:${selectedCity.latitude},${selectedCity.longitude}?q=${Uri.encode("مسجد")}")
                            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                            try {
                                context.startActivity(mapIntent)
                            } catch (_: Exception) {
                                try {
                                    val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode("مسجد")}&center=${selectedCity.latitude},${selectedCity.longitude}")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                } catch (_: Exception) {
                                }
                            }
                        }
                    )
                }
            }

            // Daily Prayer Tracker & Streak Card ("سجل صلواتي اليوم وسلسلة الالتزام")
            item {
                val trackablePrayers = listOf(
                    PrayerType.FAJR,
                    PrayerType.DHUHR,
                    PrayerType.ASR,
                    PrayerType.MAGHRIB,
                    PrayerType.ISHA
                )
                val completedCount = trackablePrayers.count { uiState.completedPrayersToday.contains(it.name) }

                LiquidGlassCard(
                    cornerRadius = 22.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("daily_prayer_tracker_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "سجل صلواتي اليوم ($completedCount / 5)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (completedCount == 5) "ما شاء الله! أتممت صلوات اليوم كاملة ✨" else "اضغط على كل صلاة بعد أدائها لبناء سلسلتك اليومية",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xBBFFFFFF)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (uiState.prayerStreakDays > 0) GoldAccent.copy(alpha = 0.25f) else Color(0x22FFFFFF),
                                border = BorderStroke(
                                    1.dp,
                                    if (uiState.prayerStreakDays > 0) GoldAccent else Color(0x33FFFFFF)
                                )
                            ) {
                                Text(
                                    text = "🔥 سلسلة الالتزام: ${uiState.prayerStreakDays} يوم",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldLight,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            trackablePrayers.forEach { pType ->
                                val isDone = uiState.completedPrayersToday.contains(pType.name)
                                val bgColor by animateColorAsState(
                                    targetValue = if (isDone) EmeraldPrimary else Color(0x22FFFFFF),
                                    animationSpec = tween(220),
                                    label = "tracker_bg_${pType.name}"
                                )
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewModel.togglePrayerCompleted(pType) }
                                        .testTag("tracker_prayer_${pType.name}"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = bgColor,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDone) EmeraldLight else Color(0x33FFFFFF)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = pType.titleAr,
                                            tint = if (isDone) Color.White else Color(0x99FFFFFF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = pType.titleAr,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isDone) FontWeight.Bold else FontWeight.Medium,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Adhan Voice & Notification Quick Bar ("الله أكبر الله أكبر")
            item {
                LiquidGlassCard(
                    cornerRadius = 18.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prayer_screen_adhan_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (uiState.isAdhanNotificationEnabled) EmeraldContainer.copy(alpha = 0.5f) else Color(0x33FFFFFF),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isAdhanNotificationEnabled) Icons.Default.Campaign else Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = if (uiState.isAdhanNotificationEnabled) EmeraldPrimary else Color.White,
                                    modifier = Modifier
                                        .padding(9.dp)
                                        .size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "إشعار الأذان: الله أكبر الله أكبر",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "بصوت: ${uiState.selectedAdhanVoice.nameAr} (${uiState.selectedAdhanVoice.placeAr})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoldLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Preview / Test Button
                        val isPreviewingCurrent = uiState.previewingVoiceId == uiState.selectedAdhanVoice.id
                        FilledTonalButton(
                            onClick = { viewModel.previewAdhanVoice(uiState.selectedAdhanVoice) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isPreviewingCurrent) GoldAccent else Color(0x33FFFFFF),
                                contentColor = if (isPreviewingCurrent) Color.Black else Color.White
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPreviewingCurrent) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "استماع للتكبيرات",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPreviewingCurrent) "إيقاف" else "استماع",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Kerāhet Alert if active
            if (times?.isKerahat == true && times.kerahatMessage != null) {
                item {
                    LiquidGlassCard(
                        cornerRadius = 18.dp,
                        containerBrush = Brush.horizontalGradient(
                            colors = listOf(Color(0x805C3300), Color(0x664A2B00))
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("kerahat_warning_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "تنبيه وقت الكراهة",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                                Text(
                                    text = times.kerahatMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                            }
                        }
                    }
                }
            }

            // Prayer Times Section Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "جدول مواقيت الصلاة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "طريقة الحساب: ${uiState.calculationMethod.titleAr}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xB3FFFFFF)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x33000000),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                    ) {
                        Text(
                            text = "توقيت: $cityNameDisplay",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Prayer Time Items in Liquid Glass Rows
            val prayerItemList = times?.items ?: emptyList()
            items(prayerItemList, key = { it.type.name }) { item ->
                LiquidGlassPrayerTimeRow(item = item)
            }

            // دعاء اليوم المختار - بطاقة زجاجية متزامنة يومياً
            item {
                Spacer(modifier = Modifier.height(4.dp))
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("daily_dua_card"),
                    cornerRadius = 24.dp,
                    onClick = onNavigateToDua
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "دعاء اليوم",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                                if (uiState.dailyDuaDateFormatted.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(${uiState.dailyDuaDateFormatted})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xB3FFFFFF)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.isDailyDuaOnlineUpdated) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = EmeraldLight.copy(alpha = 0.25f)
                                    ) {
                                        Text(
                                            text = "محدّث أونلاين",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldLight,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "مكتبة الأدعية >",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GoldAccent
                                )
                            }
                        }

                        if (uiState.dailyDua.titleAr.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.dailyDua.titleAr,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoldAccent
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = uiState.dailyDua.arabicText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                lineHeight = 28.sp
                            ),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (uiState.dailyDua.source.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "المصدر: ${uiState.dailyDua.source}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xCCFFFFFF)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    ShareCardGenerator.shareStoryCard(
                                        context = context,
                                        badgeLabel = "دعاء اليوم",
                                        title = uiState.dailyDua.titleAr,
                                        arabicBody = uiState.dailyDua.arabicText,
                                        source = uiState.dailyDua.source,
                                        cityAndDate = "$cityNameDisplay • ${times?.hijriFormatted ?: uiState.dailyDuaDateFormatted}"
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 46.dp)
                                    .testTag("share_daily_dua_story_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "مشاركة للستوري",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "مشاركة كصورة للستوري 📸",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // City & Country Picker Modal
        if (uiState.isCityPickerOpen) {
            CityPickerDialog(
                cities = uiState.filteredCities,
                selectedCity = uiState.selectedCity,
                searchQuery = uiState.citySearchQuery,
                selectedRegion = uiState.selectedRegion,
                onSearchQueryChange = { viewModel.setCitySearchQuery(it) },
                onRegionSelected = { viewModel.selectRegionFilter(it) },
                onCitySelected = { viewModel.selectCity(it) },
                onDismiss = { viewModel.openCityPicker(false) },
                onGpsLocate = {
                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (hasFine || hasCoarse) {
                        viewModel.requestGpsLocation(context)
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            )
        }

        // 12-Month Schedule & Imsakiah Modal Dialog
        if (uiState.showMonthlyImsakiahDialog) {
            val nowCal = java.util.Calendar.getInstance()
            val todayDay = nowCal.get(java.util.Calendar.DAY_OF_MONTH)
            val todayMonth = nowCal.get(java.util.Calendar.MONTH) + 1

            MonthlyImsakiahDialog(
                cityName = if (uiState.selectedCity.nameAr.isNotEmpty()) "${uiState.selectedCity.nameAr} (${uiState.selectedCity.countryAr})" else uiState.selectedCity.name,
                selectedMonth = uiState.selectedCalendarMonth,
                selectedYear = uiState.selectedCalendarYear,
                daysList = uiState.monthlyImsakiahDays,
                currentDayNumber = todayDay,
                currentMonthNumber = todayMonth,
                onMonthSelected = { viewModel.selectCalendarMonth(it) },
                onDaySelected = { day, month, year -> viewModel.selectDayFromCalendar(day, month, year) },
                onDismiss = { viewModel.openMonthlyImsakiah(false) }
            )
        }

        // Quran & Daily Wird Reader Dialog
        if (uiState.showQuranWirdDialog) {
            QuranWirdDialog(
                lastReadSurahId = uiState.lastReadSurahId,
                fontSizeSp = uiState.quranFontSizeSp,
                isTodayWirdCompleted = uiState.isTodayWirdCompleted,
                totalWirdsCount = uiState.totalWirdsCount,
                cityAndDateLabel = "$cityNameDisplay • ${times?.hijriFormatted ?: ""}",
                onSelectSurahBookmark = { viewModel.selectSurahBookmark(it) },
                onAdjustFontSize = { viewModel.adjustQuranFontSize(it) },
                onToggleWirdCompleted = { viewModel.toggleTodayWirdCompleted() },
                onDismiss = { viewModel.openQuranWird(false) }
            )
        }
    }
}

@Composable
private fun QuickFeaturePill(
    title: String,
    subtitle: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidGlassCard(
        cornerRadius = 18.dp,
        onClick = onClick,
        containerBrush = if (highlight) Brush.verticalGradient(
            colors = listOf(Color(0x88135D48), Color(0x660B3B2D))
        ) else null,
        modifier = modifier.defaultMinSize(minHeight = 64.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) GoldLight else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (highlight) EmeraldLight else Color(0xBBFFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun LiquidGlassPrayerTimeRow(item: PrayerItem) {
    val isCurrent = item.isCurrent
    val isNext = item.isNext

    val currentBlur = LocalWaterOpacity.current
    val containerBrush = when {
        isNext -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF1B5E4A).copy(alpha = 0.15f + 0.75f * currentBlur),
                Color(0xFF2A7D65).copy(alpha = 0.10f + 0.70f * currentBlur)
            )
        )
        isCurrent -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF7A5B00).copy(alpha = 0.15f + 0.70f * currentBlur),
                Color(0xFF5A4200).copy(alpha = 0.10f + 0.65f * currentBlur)
            )
        )
        else -> null
    }

    val icon: ImageVector = when (item.type) {
        PrayerType.FAJR -> Icons.Default.NightsStay
        PrayerType.SUNRISE -> Icons.Default.WbTwilight
        PrayerType.DHUHR -> Icons.Default.WbSunny
        PrayerType.ASR -> Icons.Default.BrightnessMedium
        PrayerType.MAGHRIB -> Icons.Default.BrightnessLow
        PrayerType.ISHA -> Icons.Default.Bedtime
    }

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("prayer_item_${item.type.name}"),
        cornerRadius = 20.dp,
        containerBrush = containerBrush
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Icon + Arabic Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (isNext) Color(0x40D4AF37) else Color(0x28FFFFFF),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isNext) GoldLight else Color.White,
                        modifier = Modifier
                            .padding(9.dp)
                            .size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.type.titleAr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isNext || isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                            color = Color.White
                        )
                        if (isNext) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GoldAccent
                            ) {
                                Text(
                                    text = "القادمة",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F1600),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (isCurrent) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x40D4AF37)
                            ) {
                                Text(
                                    text = "الوقت الحالي",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    val prayerSubtitle = when (item.type) {
                        PrayerType.FAJR -> "صلاة الفجر المشهودة"
                        PrayerType.SUNRISE -> "شروق الشمس وانتهاء وقت الفجر"
                        PrayerType.DHUHR -> "صلاة الظهر"
                        PrayerType.ASR -> "صلاة العصر"
                        PrayerType.MAGHRIB -> "صلاة المغرب"
                        PrayerType.ISHA -> "صلاة العشاء"
                    }
                    Text(
                        text = prayerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }
            }

            // Right: Prayer Time
            Text(
                text = item.timeFormatted,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = if (isNext) GoldLight else Color.White
            )
        }
    }
}
