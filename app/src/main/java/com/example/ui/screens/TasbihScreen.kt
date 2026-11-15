package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.TasbihItem
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.viewmodel.AppUiState
import com.example.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasbihScreen(
    viewModel: AppViewModel,
    uiState: AppUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val buttonScale = remember { Animatable(1f) }
    var showDhikrListSheet by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showEditChallengeDialog by remember { mutableStateOf(false) }

    val activeTasbih = uiState.selectedTasbih ?: uiState.tasbihList.firstOrNull()

    // Full screen mode handler
    if (uiState.isFullScreenTasbih) {
        BackHandler {
            viewModel.setFullScreenTasbih(false)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF041812),
                            Color(0xFF08261E),
                            Color(0xFF020D0A)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    coroutineScope.launch {
                        buttonScale.animateTo(0.92f, spring(dampingRatio = 0.5f, stiffness = 800f))
                        buttonScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f))
                    }
                    viewModel.incrementTasbih()
                }
                .testTag("fullscreen_tasbih_surface"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = activeTasbih?.titleAr ?: "تسبيح",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
                    color = GoldLight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (!activeTasbih?.meaningTr.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activeTasbih?.meaningTr ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Giant Animated Number
                Text(
                    text = "${activeTasbih?.currentCount ?: 0}",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 110.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color.White,
                    modifier = Modifier.scale(buttonScale.value)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if ((activeTasbih?.target ?: 0) > 0) {
                            "الهدف: ${activeTasbih?.target} • الدورة: ${activeTasbih?.lapCount}"
                        } else {
                            "غير محدود • المجموع: ${activeTasbih?.totalCount}"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = GoldLight,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Top exit button
            IconButton(
                onClick = { viewModel.setFullScreenTasbih(false) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .testTag("exit_fullscreen_button")
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "خروج من ملء الشاشة",
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Bottom tip
            Text(
                text = "المس أي مكان على الشاشة للتسبيح والعد",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            )
        }
        return
    }

    // Standard Modern Layout
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Active Dhikr Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDhikrListSheet = true }
                    .testTag("active_dhikr_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = activeTasbih?.category ?: "أذكار الصلاة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تغيير الذكر",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = activeTasbih?.titleAr ?: "سُبْحَانَ اللَّهِ",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (!activeTasbih?.meaningTr.isNullOrEmpty()) {
                            Text(
                                text = activeTasbih?.meaningTr ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "تبديل الذكر",
                        tint = GoldAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Target Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                listOf(33, 99, 100, 0).forEach { targetValue ->
                    val isSelected = (activeTasbih?.target ?: 33) == targetValue
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setTarget(targetValue) },
                        label = {
                            Text(if (targetValue == 0) "غير محدود (∞)" else "$targetValue")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("target_chip_$targetValue")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Central Tactile Beads Dial & Counting Button
            val count = activeTasbih?.currentCount ?: 0
            val target = activeTasbih?.target ?: 33
            val lap = activeTasbih?.lapCount ?: 0

            Box(
                modifier = Modifier
                    .size(280.dp)
                    .testTag("tasbih_counter_box"),
                contentAlignment = Alignment.Center
            ) {
                // Circular Canvas with 33 interactive beads around the perimeter
                val beadCount = if (target in 1..99) target.coerceIn(11, 33) else 33
                val activeBeadIndex = if (target > 0) ((count.toFloat() / target.toFloat()) * beadCount).toInt() else (count % beadCount)
                val primaryColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.outlineVariant

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2 - 16.dp.toPx()
                    val center = Offset(size.width / 2, size.height / 2)

                    // Background track ring
                    drawCircle(
                        color = outlineColor.copy(alpha = 0.25f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Draw circular beads
                    for (i in 0 until beadCount) {
                        val angle = (i.toDouble() / beadCount.toDouble()) * 2 * Math.PI - (Math.PI / 2)
                        val x = center.x + (radius * cos(angle)).toFloat()
                        val y = center.y + (radius * sin(angle)).toFloat()

                        val isBeadCompleted = i < activeBeadIndex
                        val isCurrentBead = i == activeBeadIndex

                        val beadRadius = when {
                            isCurrentBead -> 7.dp.toPx()
                            isBeadCompleted -> 5.5.dp.toPx()
                            else -> 4.5.dp.toPx()
                        }

                        val beadColor = when {
                            isCurrentBead -> GoldAccent
                            isBeadCompleted -> primaryColor
                            else -> outlineColor.copy(alpha = 0.5f)
                        }

                        drawCircle(
                            color = beadColor,
                            radius = beadRadius,
                            center = Offset(x, y)
                        )
                    }
                }

                // Inner Main Tap Button
                Surface(
                    onClick = {
                        coroutineScope.launch {
                            buttonScale.animateTo(0.93f, spring(dampingRatio = 0.5f, stiffness = 800f))
                            buttonScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f))
                        }
                        viewModel.incrementTasbih()
                    },
                    modifier = Modifier
                        .size(210.dp)
                        .scale(buttonScale.value)
                        .testTag("tasbih_tap_button"),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(
                        width = 3.dp,
                        brush = Brush.radialGradient(
                            colors = listOf(GoldAccent, MaterialTheme.colorScheme.primary)
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "سبِّح",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 58.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = if (target > 0) "الهدف: $target" else "غير محدود",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Shared Group Dhikr Challenge Card
            val challengeTarget = uiState.sharedChallengeTarget.coerceAtLeast(10)
            val challengeProgress = (uiState.sharedChallengeMyCount.toFloat() / challengeTarget.toFloat()).coerceIn(0f, 1f)
            val isChallengeComplete = uiState.sharedChallengeMyCount >= challengeTarget

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shared_dhikr_challenge_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF072117)
                ),
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "تحدي الذكر المشترك 🤝",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                                Text(
                                    text = uiState.sharedChallengeTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { showEditChallengeDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "تعديل التحدي",
                                tint = GoldLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isChallengeComplete) "ما شاء الله! حققت هدف التحدي ✨" else "مساهمتك في التحدي:",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isChallengeComplete) EmeraldLight else Color(0xCCFFFFFF)
                        )
                        Text(
                            text = "${uiState.sharedChallengeMyCount} / $challengeTarget",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = GoldLight
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { challengeProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (isChallengeComplete) EmeraldLight else GoldAccent,
                        trackColor = Color(0x33FFFFFF)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val inviteText = "🤝 دعوة للخير والذكر الجماعي\n\n" +
                                    "أشاركك في تحدي: «${uiState.sharedChallengeTitle}»\n" +
                                    "هدفنا الوصول إلى $challengeTarget تسبيحة معاً، وقد أتممت حتى الآن ${uiState.sharedChallengeMyCount}!\n\n" +
                                    "انضم إلينا الآن وشارك الأجر عبر تطبيق «صلاتي وأذكاري» ✨"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, inviteText)
                            }
                            context.startActivity(Intent.createChooser(intent, "دعوة الأهل والأصدقاء للتحدي"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("invite_dhikr_challenge_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "دعوة",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "دعوة الأهل والأصدقاء للتحدي 🤝",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Action Bar: Sound, Vibration, Fullscreen, Reset, Add
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 110.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sound Toggle
                FilledTonalIconButton(
                    onClick = { viewModel.toggleSound() },
                    modifier = Modifier.testTag("sound_toggle_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "الصوت",
                        tint = if (uiState.isSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Vibration Toggle
                FilledTonalIconButton(
                    onClick = { viewModel.toggleVibration() },
                    modifier = Modifier.testTag("vibration_toggle_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isVibrationEnabled) Icons.Default.Vibration else Icons.Default.Smartphone,
                        contentDescription = "الاهتزاز",
                        tint = if (uiState.isVibrationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Fullscreen Mode Button
                FilledTonalIconButton(
                    onClick = { viewModel.setFullScreenTasbih(true) },
                    modifier = Modifier.testTag("fullscreen_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "وضع ملء الشاشة",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Reset Button
                FilledTonalIconButton(
                    onClick = { showResetConfirmDialog = true },
                    modifier = Modifier.testTag("reset_tasbih_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "إعادة ضبط",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                // Add Custom Dhikr Button
                FilledTonalIconButton(
                    onClick = { viewModel.setShowAddTasbihDialog(true) },
                    modifier = Modifier.testTag("add_custom_dhikr_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "إضافة ذكر جديد",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Reset Confirmation Dialog
        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                title = { Text("إعادة ضبط العداد") },
                text = { Text("هل تريد تصفير عداد الذكر الحالي وعدد الدورات؟") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.resetCurrentTasbih()
                            showResetConfirmDialog = false
                        },
                        modifier = Modifier.testTag("confirm_reset_button")
                    ) {
                        Text("إعادة ضبط", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirmDialog = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }

        // Dhikr Selection BottomSheet
        if (showDhikrListSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDhikrListSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                modifier = Modifier.testTag("dhikr_picker_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "اختيار الذكر والتسبيح",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = {
                            showDhikrListSheet = false
                            viewModel.setShowAddTasbihDialog(true)
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة جديد")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.tasbihList, key = { it.id }) { item ->
                            val isSelected = item.id == activeTasbih?.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectTasbih(item)
                                        showDhikrListSheet = false
                                    }
                                    .testTag("dhikr_item_${item.id}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.titleAr.ifEmpty { item.titleTr },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (item.isCustom) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = GoldAccent.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "مخصص",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = GoldAccent,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (item.meaningTr.isNotEmpty()) {
                                            Text(
                                                text = item.meaningTr,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }

                                        Text(
                                            text = "الهدف: ${if (item.target > 0) item.target else "غير محدود"} • المجموع: ${item.totalCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Custom Dhikr Dialog
        if (uiState.showAddTasbihDialog) {
            AddCustomTasbihDialog(
                onDismiss = { viewModel.setShowAddTasbihDialog(false) },
                onAdd = { titleTr, titleAr, meaningTr, target ->
                    viewModel.addCustomTasbih(titleTr, titleAr, meaningTr, target)
                }
            )
        }

        // Edit Shared Challenge Dialog
        if (showEditChallengeDialog) {
            EditSharedChallengeDialog(
                initialTitle = uiState.sharedChallengeTitle,
                initialTarget = uiState.sharedChallengeTarget,
                onDismiss = { showEditChallengeDialog = false },
                onSave = { newTitle, newTarget, resetCount ->
                    viewModel.updateSharedChallenge(newTitle, newTarget, resetCount)
                    showEditChallengeDialog = false
                }
            )
        }
    }
}

@Composable
fun EditSharedChallengeDialog(
    initialTitle: String,
    initialTarget: Int,
    onDismiss: () -> Unit,
    onSave: (title: String, target: Int, resetProgress: Boolean) -> Unit
) {
    var titleText by remember { mutableStateOf(initialTitle) }
    var targetText by remember { mutableStateOf(initialTarget.toString()) }
    var resetProgress by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("edit_challenge_dialog")
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "إعداد تحدي الذكر المشترك",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("اسم التحدي أو الذكر") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter { c -> c.isDigit() } },
                    label = { Text("الهدف المطلوب (مثال: 1000)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = resetProgress,
                        onCheckedChange = { resetProgress = it }
                    )
                    Text("تصفير عداد التحدي والبدء من 0", style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(titleText, targetText.toIntOrNull() ?: 1000, resetProgress)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("حفظ التحدي")
                    }
                }
            }
        }
    }
}

@Composable
fun AddCustomTasbihDialog(
    onDismiss: () -> Unit,
    onAdd: (titleTr: String, titleAr: String, meaningTr: String, target: Int) -> Unit
) {
    var titleAr by remember { mutableStateOf("") }
    var meaningAr by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("33") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_custom_dhikr_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "إضافة ذكر جديد",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = titleAr,
                    onValueChange = { titleAr = it },
                    label = { Text("نص الذكر (مثال: لا حول ولا قوة إلا بالله) *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_dhikr_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = meaningAr,
                    onValueChange = { meaningAr = it },
                    label = { Text("المعنى أو الفضل (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter { char -> char.isDigit() } },
                    label = { Text("الهدف والعدد (0 = غير محدود)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_dhikr_target_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (titleAr.isNotBlank()) {
                                val target = targetText.toIntOrNull() ?: 33
                                onAdd(titleAr.trim(), titleAr.trim(), meaningAr.trim(), target)
                            }
                        },
                        enabled = titleAr.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_custom_dhikr_button")
                    ) {
                        Text("حفظ الذكر")
                    }
                }
            }
        }
    }
}
