package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import com.example.ui.components.LocationPermissionDialog
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppUpdateDialog
import com.example.ui.screens.DuaScreen
import com.example.ui.screens.PrayerScreen
import com.example.ui.screens.QiblaScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TasbihScreen
import com.example.ui.theme.Glassmorphism
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalWaterOpacity
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppViewModel

enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    PRAYER("المواقيت", Icons.Filled.Schedule, Icons.Outlined.Schedule, "tab_prayer"),
    TASBIH("التسبيح", Icons.Filled.Adjust, Icons.Outlined.Adjust, "tab_tasbih"),
    DUA("الأدعية", Icons.Filled.MenuBook, Icons.Outlined.MenuBook, "tab_dua"),
    QIBLA("القبلة", Icons.Filled.Explore, Icons.Outlined.Explore, "tab_qibla"),
    SETTINGS("الإعدادات", Icons.Filled.Settings, Icons.Outlined.Settings, "tab_settings")
}

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: AppViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(MainTab.PRAYER) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkForUpdateOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            viewModel.requestGpsLocation(context)
        } else {
            viewModel.dismissLocationOnboarding()
        }
    }

    // Subscreen back behavior
    BackHandler(enabled = currentTab != MainTab.PRAYER && !uiState.isFullScreenTasbih) {
        currentTab = MainTab.PRAYER
    }

    CompositionLocalProvider(LocalWaterOpacity provides uiState.waterOpacity) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF04120D))
        ) {
        // Dynamic Chosen Islamic Wallpaper (Kaaba night, starry mosque, emerald water, golden art, or midnight dark)
        val wallpaper = uiState.selectedWallpaper
        if (wallpaper.isSolidColor || wallpaper.drawableRes == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF030B07))
            )
        } else {
            Image(
                painter = painterResource(id = wallpaper.drawableRes),
                contentDescription = wallpaper.nameAr,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Subtle dark translucent vignette overlay for pristine contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x55020A07),
                            Color(0x2204120E),
                            Color(0x66010805)
                        )
                    )
                )
        )

        // Main Animated Viewport with 120Hz/90Hz smooth physics-based transition
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                val isForward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialOffsetX = { if (isForward) it / 2 else -it / 2 }
                ) + fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )) togetherWith (slideOutHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    targetOffsetX = { if (isForward) -it / 3 else it / 3 }
                ) + fadeOut(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ))
            },
            label = "tab_smooth_transition",
            modifier = Modifier.fillMaxSize()
        ) { targetTab ->
            when (targetTab) {
                MainTab.PRAYER -> PrayerScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onNavigateToDua = { currentTab = MainTab.DUA },
                    modifier = Modifier.fillMaxSize()
                )
                MainTab.TASBIH -> TasbihScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    modifier = Modifier.fillMaxSize()
                )
                MainTab.DUA -> DuaScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    modifier = Modifier.fillMaxSize()
                )
                MainTab.QIBLA -> QiblaScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    modifier = Modifier.fillMaxSize()
                )
                MainTab.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Floating iOS Dynamic Island Style Liquid Glass Bottom Bar (clean layout, opacity control moved to Settings)
        if (!uiState.isFullScreenTasbih) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                val navBarBrush = Glassmorphism.dynamicGlassCardGradient(uiState.waterOpacity)
                val navBarBorder = Glassmorphism.dynamicGlassBorder(uiState.waterOpacity)

                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(navBarBrush)
                        .border(
                            width = 1.dp,
                            brush = navBarBorder,
                            shape = RoundedCornerShape(32.dp)
                        )
                        .testTag("liquid_glass_bottom_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MainTab.values().forEach { tab ->
                            val isSelected = currentTab == tab

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { currentTab = tab }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag(tab.testTag),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Animated active pill indicator
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Brush.radialGradient(
                                                    colors = listOf(
                                                        Color(0x66D4AF37),
                                                        Color(0x22135D48)
                                                    )
                                                ) else Brush.linearGradient(
                                                    listOf(Color.Transparent, Color.Transparent)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title,
                                            tint = if (isSelected) GoldAccent else Color(0xB3FFFFFF),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = tab.title,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0x99FFFFFF)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // First-Launch Location Permission & Accuracy Onboarding Dialog
        if (uiState.showLocationOnboardingDialog) {
            LocationPermissionDialog(
                onRequestPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onManualChooseCity = {
                    viewModel.dismissLocationOnboarding()
                    viewModel.openCityPicker(true)
                },
                waterOpacity = uiState.waterOpacity
            )
        }

        // In-App Remote Update Dialog
        if (uiState.showUpdateDialog) {
            val ctx = LocalContext.current
            AppUpdateDialog(
                updateInfo = uiState.updateInfo,
                onDismiss = { viewModel.dismissUpdateDialog(rememberDismissal = true) },
                onOpenTelegram = { viewModel.openUpdateInTelegram(ctx) },
                onStartDirectDownload = { viewModel.startDirectApkDownload(ctx) },
                onInstallDownloadedApk = { viewModel.installDownloadedApk(ctx) }
            )
        }
    }
}
}


