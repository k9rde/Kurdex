package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DuaData
import com.example.model.DuaItem
import com.example.model.InteractiveAdhkarData
import com.example.model.InteractiveAdhkarItem
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.util.ShareCardGenerator
import com.example.viewmodel.AppUiState
import com.example.viewmodel.AppViewModel

@Composable
fun DuaScreen(
    viewModel: AppViewModel,
    uiState: AppUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isInteractiveAdhkarMode by remember { mutableStateOf(false) }
    var selectedAdhkarPeriod by remember { mutableStateOf(InteractiveAdhkarData.periods.first()) }
    var selectedCategory by remember { mutableStateOf("الكل") }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(uiState.openInteractiveAdhkarDirect) {
        if (uiState.openInteractiveAdhkarDirect) {
            isInteractiveAdhkarMode = true
            viewModel.setOpenInteractiveAdhkarDirect(false)
        }
    }

    val cityDateLabel = remember(uiState.selectedCity, uiState.prayerTimes) {
        val cName = uiState.selectedCity.nameAr.ifEmpty { uiState.selectedCity.name }
        val hDate = uiState.prayerTimes?.hijriFormatted ?: uiState.dailyDuaDateFormatted
        "$cName • $hDate"
    }

    val filteredDuas = remember(selectedCategory, searchQuery, uiState.favoriteDuaIds, uiState.dailyDua) {
        val baseList = if (selectedCategory == "دعاء اليوم") {
            listOf(uiState.dailyDua)
        } else {
            DuaData.allDuas
        }

        baseList.filter { dua ->
            val matchesCategory = when (selectedCategory) {
                "الكل" -> true
                "المفضلة" -> uiState.favoriteDuaIds.contains(dua.id)
                "دعاء اليوم" -> true
                else -> dua.category == selectedCategory
            }

            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                    dua.titleAr.lowercase().contains(q) ||
                    dua.arabicText.contains(q) ||
                    dua.meaningAr.lowercase().contains(q) ||
                    dua.source.lowercase().contains(q) ||
                    dua.benefit.lowercase().contains(q)

            matchesCategory && matchesSearch
        }
    }

    val categories = remember {
        listOf("الكل", "دعاء اليوم", "المفضلة") + DuaData.categories.filter { it != "الكل" && it != "دعاء اليوم" }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Header Title & Online/Offline status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "مكتبة الأدعية اليومية",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "تتغير وتتجدد يومياً أوفلاين وأونلاين من القرآن الكريم والسنة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Daily Refresh Button
                IconButton(
                    onClick = { viewModel.refreshDailyDua(shuffleToNext = true) },
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("refresh_daily_dua_button")
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.isDailyDuaSyncing) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تحديث دعاء اليوم",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Sync Status feedback message (if active)
            AnimatedVisibility(
                visible = uiState.dailyDuaStatusMessage.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.dailyDuaStatusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode Toggle Bar: Library vs Interactive Adhkar Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = { isInteractiveAdhkarMode = false },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("mode_dua_library_tab"),
                    shape = RoundedCornerShape(12.dp),
                    color = if (!isInteractiveAdhkarMode) EmeraldPrimary else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "مكتبة الأدعية 📖",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (!isInteractiveAdhkarMode) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Surface(
                    onClick = { isInteractiveAdhkarMode = true },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("mode_interactive_adhkar_tab"),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isInteractiveAdhkarMode) EmeraldPrimary else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "الأذكار التفاعلية 📿",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isInteractiveAdhkarMode) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isInteractiveAdhkarMode) {
                InteractiveAdhkarDeckView(
                    selectedPeriod = selectedAdhkarPeriod,
                    onSelectPeriod = { selectedAdhkarPeriod = it },
                    progressMap = uiState.adhkarProgressMap,
                    cityDateLabel = cityDateLabel,
                    onIncrementItem = { id, target -> viewModel.incrementAdhkarItem(id, target) },
                    onResetItem = { id -> viewModel.resetAdhkarItem(id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dua_search_input"),
                placeholder = { Text("ابحث في الأدعية والأذكار...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Pills
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        leadingIcon = when (category) {
                            "المفضلة" -> {
                                {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            "دعاء اليوم" -> {
                                {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            else -> null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("dua_category_chip_$category")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dua List + Featured Daily Dua Card
            if (filteredDuas.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedCategory == "المفضلة") "لم تقم بإضافة أي دعاء إلى المفضلة بعد." else "لم يتم العثور على أي دعاء يطابق معايير البحث.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Show Hero Featured Daily Dua Card at the top when viewing All or Daily
                    if (searchQuery.isEmpty() && (selectedCategory == "الكل" || selectedCategory == "دعاء اليوم")) {
                        item(key = "featured_daily_card") {
                            FeaturedDailyDuaCard(
                                dua = uiState.dailyDua,
                                dateFormatted = uiState.dailyDuaDateFormatted,
                                isOnlineUpdated = uiState.isDailyDuaOnlineUpdated,
                                isSyncing = uiState.isDailyDuaSyncing,
                                isFavorite = uiState.favoriteDuaIds.contains(uiState.dailyDua.id),
                                onRefresh = { viewModel.refreshDailyDua(shuffleToNext = true) },
                                onToggleFavorite = { viewModel.toggleFavoriteDua(uiState.dailyDua.id) },
                                onCopy = {
                                    copyDuaToClipboard(context, uiState.dailyDua)
                                },
                                onShare = {
                                    shareDua(context, uiState.dailyDua)
                                },
                                onShareStory = {
                                    ShareCardGenerator.shareStoryCard(
                                        context = context,
                                        badgeLabel = "دعاء اليوم",
                                        title = uiState.dailyDua.titleAr,
                                        arabicBody = uiState.dailyDua.arabicText,
                                        source = uiState.dailyDua.source,
                                        cityAndDate = cityDateLabel
                                    )
                                }
                            )
                        }
                    }

                    // Remaining library list
                    items(filteredDuas, key = { it.id }) { dua ->
                        val isFavorite = uiState.favoriteDuaIds.contains(dua.id)
                        DuaCard(
                            dua = dua,
                            isFavorite = isFavorite,
                            onToggleFavorite = { viewModel.toggleFavoriteDua(dua.id) },
                            onCopy = { copyDuaToClipboard(context, dua) },
                            onShare = { shareDua(context, dua) },
                            onShareStory = {
                                ShareCardGenerator.shareStoryCard(
                                    context = context,
                                    badgeLabel = dua.category,
                                    title = dua.titleAr,
                                    arabicBody = dua.arabicText,
                                    source = dua.source,
                                    cityAndDate = cityDateLabel
                                )
                            }
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * Hero Featured Daily Dua Card with Golden-Emerald Styling, Date Badge, and Online Sync Indicators
 */
@Composable
fun FeaturedDailyDuaCard(
    dua: DuaItem,
    dateFormatted: String,
    isOnlineUpdated: Boolean,
    isSyncing: Boolean,
    isFavorite: Boolean,
    onRefresh: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onShareStory: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("featured_daily_dua_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF071E15)
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldAccent.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Featured Title + Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0x33D4AF37),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "دعاء اليوم المبارك",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                }

                // Online/Offline status badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOnlineUpdated) Color(0x3348BB78) else Color(0x33D4AF37)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isOnlineUpdated) Icons.Default.Wifi else Icons.Default.Sync,
                            contentDescription = null,
                            tint = if (isOnlineUpdated) Color(0xFF68D391) else GoldLight,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isOnlineUpdated) "محدّث أونلاين" else "التقويم اليومي (أوفلاين)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOnlineUpdated) Color(0xFF9AE6B4) else GoldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (dateFormatted.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xAAFFFFFF)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = dua.titleAr,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Arabic Text (Right-aligned, prominent)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0x40000000),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = dua.arabicText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        lineHeight = 36.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFF9E6),
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Meaning
            Text(
                text = "المعنى والشرح:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = GoldLight
            )
            Text(
                text = dua.meaningAr,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xDDFFFFFF)
            )

            // Source & Benefit
            if (dua.source.isNotEmpty() || dua.benefit.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = Color(0x33FFFFFF))
                Spacer(modifier = Modifier.height(8.dp))

                if (dua.source.isNotEmpty()) {
                    Text(
                        text = "📖 ${dua.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xAAFFFFFF)
                    )
                }

                if (dua.benefit.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "✨ ${dua.benefit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Footer Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Next / Shuffle button
                Button(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x33D4AF37),
                        contentColor = GoldLight
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = GoldLight
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تغيير وتحديث دعاء اليوم",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShareStory, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "مشاركة صورة للستوري",
                            tint = GoldLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "نسخ",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "مشاركة",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "المفضلة",
                            tint = if (isFavorite) GoldAccent else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DuaCard(
    dua: DuaItem,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onShareStory: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dua_card_${dua.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Category and Action icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = dua.category,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onShareStory,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "تصميم ستوري",
                            tint = GoldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "نسخ",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "مشاركة",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "إضافة للمفضلة",
                            tint = if (isFavorite) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = dua.titleAr,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Arabic Text (Right-aligned, large font)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = dua.arabicText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        lineHeight = 36.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Meaning
            Text(
                text = "المعنى والبيان:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = dua.meaningAr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Source & Benefit
            if (dua.source.isNotEmpty() || dua.benefit.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                if (dua.source.isNotEmpty()) {
                    Text(
                        text = "📖 ${dua.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (dua.benefit.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✨ ${dua.benefit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAccent
                    )
                }
            }
        }
    }
}

private fun copyDuaToClipboard(context: Context, dua: DuaItem) {
    val textToCopy = "${dua.titleAr}\n\n${dua.arabicText}\n\nالمعنى: ${dua.meaningAr}\n\nالمصدر: ${dua.source}\n\n— صلاتي وأذكاري"
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Dua", textToCopy))
    Toast.makeText(context, "تم نسخ نص الدعاء بنجاح", Toast.LENGTH_SHORT).show()
}

private fun shareDua(context: Context, dua: DuaItem) {
    val shareText = "${dua.titleAr}\n\n${dua.arabicText}\n\nالمعنى: ${dua.meaningAr}\n\nالمصدر: ${dua.source}\n\n— صلاتي وأذكاري"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, dua.titleAr)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة الدعاء"))
}

@Composable
fun InteractiveAdhkarDeckView(
    selectedPeriod: String,
    onSelectPeriod: (String) -> Unit,
    progressMap: Map<String, Int>,
    cityDateLabel: String,
    onIncrementItem: (String, Int) -> Unit,
    onResetItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val itemsInPeriod = remember(selectedPeriod) {
        InteractiveAdhkarData.allItems.filter { it.period == selectedPeriod }
    }
    val completedInPeriod = itemsInPeriod.count { (progressMap[it.id] ?: 0) >= it.targetCount }

    Column(modifier = modifier) {
        // Horizontal period selector
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(InteractiveAdhkarData.periods) { period ->
                val periodItems = InteractiveAdhkarData.allItems.filter { it.period == period }
                val doneCount = periodItems.count { (progressMap[it.id] ?: 0) >= it.targetCount }
                val isSelected = selectedPeriod == period

                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPeriod(period) },
                    label = {
                        Text(
                            text = "$period ($doneCount/${periodItems.size})",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldPrimary,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("adhkar_period_chip_$period")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Period progress banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = EmeraldPrimary.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, EmeraldLight.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "إنجاز $selectedPeriod: $completedInPeriod من ${itemsInPeriod.size}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "اضغط على البطاقة للعد",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(itemsInPeriod, key = { it.id }) { item ->
                val currentCount = (progressMap[item.id] ?: 0).coerceAtMost(item.targetCount)
                val isCompleted = currentCount >= item.targetCount
                val prog = if (item.targetCount > 0) currentCount.toFloat() / item.targetCount.toFloat() else 1f

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onIncrementItem(item.id, item.targetCount) }
                        .testTag("adhkar_card_${item.id}"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCompleted) Color(0xFF0A2E20) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = if (isCompleted) 1.5.dp else 1.dp,
                        color = if (isCompleted) EmeraldLight else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
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
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCompleted) EmeraldPrimary else GoldAccent.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (isCompleted) "اكتمل ✓" else "$currentCount / ${item.targetCount}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isCompleted) Color.White else GoldAccent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCompleted) Color.White else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        ShareCardGenerator.shareStoryCard(
                                            context = context,
                                            badgeLabel = item.period,
                                            title = item.title,
                                            arabicBody = item.arabicText,
                                            source = item.virtue,
                                            cityAndDate = cityDateLabel
                                        )
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "مشاركة للستوري",
                                        tint = GoldAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (currentCount > 0) {
                                    IconButton(
                                        onClick = { onResetItem(item.id) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "إعادة العداد",
                                            tint = if (isCompleted) Color(0xCCFFFFFF) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = item.arabicText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 19.sp,
                                lineHeight = 32.sp
                            ),
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) Color(0xFFFFFAEB) else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "✨ ${item.virtue}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCompleted) GoldLight else MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { prog },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (isCompleted) EmeraldLight else GoldAccent,
                            trackColor = Color(0x33888888)
                        )
                    }
                }
            }
        }
    }
}
