package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OnlinePrayerSyncManager
import com.example.data.PreferencesManager
import com.example.model.AdhanVoice
import com.example.model.AppUpdateInfo
import com.example.model.AppWallpaper
import com.example.model.CalculationMethod
import com.example.model.City
import com.example.model.DayPrayerTimes
import com.example.model.DuaData
import com.example.model.DuaItem
import com.example.model.InteractiveAdhkarData
import com.example.model.JuristicMethod
import com.example.model.PrayerItem
import com.example.model.PrayerType
import com.example.model.TasbihItem
import com.example.service.PrayerTimeNotificationService
import com.example.util.AdhanNotificationHelper
import com.example.util.AppUpdateChecker
import com.example.util.CityData
import com.example.util.PrayerCalculator
import com.example.util.SoundAndHapticHelper
import com.example.widget.PrayerWidgetProvider
import com.example.worker.PrayerSyncWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

data class AppUiState(
    // Prayer state
    val selectedCity: City = CityData.defaultCity,
    val prayerTimes: DayPrayerTimes? = null,
    val dayOffset: Int = 0,
    val calculationMethod: CalculationMethod = CalculationMethod.MWL,
    val juristicMethod: JuristicMethod = JuristicMethod.STANDARD,
    val isCityPickerOpen: Boolean = false,
    val citySearchQuery: String = "",
    val selectedRegion: String = "الكل",
    val filteredCities: List<City> = CityData.allCities,

    // Online Sync Status
    val isOnlineSyncing: Boolean = false,
    val isOnlineSynced: Boolean = false,
    val syncStatusMessage: String = "",
    val isAutoSyncEnabled: Boolean = true,
    val lastSyncFormatted: String = "",

    // Tasbih state
    val tasbihList: List<TasbihItem> = emptyList(),
    val selectedTasbih: TasbihItem? = null,
    val isSoundEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val totalAllTimeCount: Int = 0,
    val isFullScreenTasbih: Boolean = false,
    val showAddTasbihDialog: Boolean = false,

    // Dua state
    val selectedDuaCategory: String = "الكل",
    val duaSearchQuery: String = "",
    val favoriteDuaIds: Set<String> = emptySet(),
    val dailyDua: DuaItem = DuaData.getDailyDuaForDate(),
    val isDailyDuaOnlineUpdated: Boolean = false,
    val dailyDuaDateFormatted: String = "",
    val isDailyDuaSyncing: Boolean = false,
    val dailyDuaStatusMessage: String = "",

    // Qibla state
    val qiblaAngle: Double = 0.0,
    val kaabaDistanceKm: Int = 0,
    val compassAzimuth: Float = 0f,
    val isQiblaAligned: Boolean = false,

    // Liquid Glass Water Interface Opacity (0.15f to 1.0f)
    val waterOpacity: Float = 0.85f,
    val showOpacityControlSheet: Boolean = false,

    // Adhan Phone Notification and Imam Voices state ("الله أكبر الله أكبر")
    val isAdhanNotificationEnabled: Boolean = true,
    val selectedAdhanVoiceId: String = "alafasy",
    val selectedAdhanVoice: AdhanVoice = AdhanVoice.getById("alafasy"),
    val availableAdhanVoices: List<AdhanVoice> = AdhanVoice.ALL_VOICES,
    val previewingVoiceId: String? = null,
    val adhanFeedbackMessage: String = "",

    // App Wallpapers state
    val selectedWallpaper: AppWallpaper = AppWallpaper.getById("kaaba_night"),
    val availableWallpapers: List<AppWallpaper> = AppWallpaper.ALL_WALLPAPERS,

    // Location Onboarding & High-precision GPS state
    val showLocationOnboardingDialog: Boolean = false,
    val magneticDeclination: Float = 0f,
    val isGpsLocating: Boolean = false,
    val gpsAccuracyMeters: Float? = null,
    val isExactGpsLocation: Boolean = false,
    val lastGpsFixTimeMs: Long = 0L,
    val locationMessage: String = "",

    // Manual prayer minute adjustments (+/- minutes)
    val prayerOffsets: Map<String, Int> = emptyMap(),

    // Monthly 12-Month Schedule & Imsakiah state
    val showMonthlyImsakiahDialog: Boolean = false,
    val selectedCalendarMonth: Int = 11,
    val selectedCalendarYear: Int = 2026,
    val monthlyImsakiahDays: List<DayPrayerTimes> = emptyList(),

    // In-App Update state
    val showUpdateDialog: Boolean = false,
    val updateInfo: AppUpdateInfo = AppUpdateInfo(),
    val isCheckingForUpdate: Boolean = false,
    val updateCheckFeedback: String = "",
    val isDeveloperModeEnabled: Boolean = false,
    val developerModeFeedback: String = "",
    val customUpdateUrl: String = "",

    // Daily Prayer Tracker & Streak + Iqama Countdown
    val completedPrayersToday: Set<String> = emptySet(),
    val prayerStreakDays: Int = 0,
    val iqamaRemainingText: String = "",

    // Quran & Daily Wird Reader
    val showQuranWirdDialog: Boolean = false,
    val lastReadSurahId: String = "fatiha",
    val quranFontSizeSp: Float = 21f,
    val isTodayWirdCompleted: Boolean = false,
    val totalWirdsCount: Int = 0,

    // Interactive Adhkar & Shared Group Challenge
    val adhkarProgressMap: Map<String, Int> = emptyMap(),
    val openInteractiveAdhkarDirect: Boolean = false,
    val sharedChallengeTitle: String = "الصلاة على النبي ﷺ (تحدي اليوم)",
    val sharedChallengeTarget: Int = 1000,
    val sharedChallengeMyCount: Int = 0,

    // Extra Notification Modes
    val isPrePrayerReminderEnabled: Boolean = true,
    val isPostPrayerFollowupEnabled: Boolean = true,
    val isOngoingCountdownEnabled: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    val feedbackHelper = SoundAndHapticHelper(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var lastWidgetUpdateKey: String = ""
    private var lastOngoingMinute: Long = -1L
    private var lastAlarmScheduleKey: String = ""
    private var lastUpdateCheckTimestampMs: Long = 0L
    private var updateCheckJob: kotlinx.coroutines.Job? = null

    init {
        val savedCity = prefs.getSelectedCity()
        val calcMethods = CalculationMethod.values()
        val juristicMethods = JuristicMethod.values()
        val calcMethod = calcMethods.getOrElse(prefs.calculationMethodIndex) { CalculationMethod.MWL }
        val juristicMethod = juristicMethods.getOrElse(prefs.juristicMethodIndex) { JuristicMethod.STANDARD }

        val tasbihs = prefs.loadTasbihs()
        val selectedTasbihId = prefs.getSelectedTasbihId()
        val activeTasbih = tasbihs.find { it.id == selectedTasbihId } ?: tasbihs.firstOrNull()

        val voiceId = prefs.selectedAdhanVoiceId
        val isAdhanNotif = prefs.isAdhanNotificationEnabled

        val wallpaperId = prefs.selectedWallpaperId
        val wallpaper = AppWallpaper.getById(wallpaperId)

        val currentOffsets = mapOf(
            "fajr" to prefs.getPrayerOffset("fajr"),
            "sunrise" to prefs.getPrayerOffset("sunrise"),
            "dhuhr" to prefs.getPrayerOffset("dhuhr"),
            "asr" to prefs.getPrayerOffset("asr"),
            "maghrib" to prefs.getPrayerOffset("maghrib"),
            "isha" to prefs.getPrayerOffset("isha")
        )

        val cal = Calendar.getInstance()
        val todayDateKey = "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH) + 1}_${cal.get(Calendar.DAY_OF_MONTH)}"
        if (prefs.lastDailyDuaDate != todayDateKey) {
            prefs.lastDailyDuaDate = todayDateKey
            prefs.dailyDuaShuffleOffset = 0
            prefs.onlineDailyDuaJson = null
        }

        val dayNamesAr = listOf("الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
        val dayName = dayNamesAr.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 1) { "" }
        val monthNamesAr = listOf("كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران", "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول")
        val monthName = monthNamesAr.getOrElse(cal.get(Calendar.MONTH)) { "" }
        val todayDuaDateFormatted = "$dayName، ${cal.get(Calendar.DAY_OF_MONTH)} $monthName ${cal.get(Calendar.YEAR)}م"

        val initialDailyDua = loadDailyDua(cal, prefs.dailyDuaShuffleOffset)

        val installedCode = AppUpdateChecker.getInstalledVersionCode(application)
        val installedName = AppUpdateChecker.getInstalledVersionName(application)

        val initialAdhkarMap = InteractiveAdhkarData.allItems.associate { item ->
            item.id to prefs.getAdhkarProgress(todayDateKey, item.id)
        }

        _uiState.update {
            it.copy(
                selectedCity = savedCity,
                calculationMethod = calcMethod,
                juristicMethod = juristicMethod,
                isSoundEnabled = prefs.isSoundEnabled,
                isVibrationEnabled = prefs.isVibrationEnabled,
                isAutoSyncEnabled = prefs.isAutoSyncOnlineEnabled,
                totalAllTimeCount = prefs.getTotalAllTimeTasbih(),
                tasbihList = tasbihs,
                selectedTasbih = activeTasbih,
                favoriteDuaIds = prefs.getFavoriteDuaIds(),
                filteredCities = CityData.findCity("", "الكل"),
                waterOpacity = prefs.waterOpacity,
                isAdhanNotificationEnabled = isAdhanNotif,
                selectedAdhanVoiceId = voiceId,
                selectedAdhanVoice = AdhanVoice.getById(voiceId),
                availableAdhanVoices = AdhanVoice.ALL_VOICES,
                selectedWallpaper = wallpaper,
                availableWallpapers = AppWallpaper.ALL_WALLPAPERS,
                prayerOffsets = currentOffsets,
                dailyDua = initialDailyDua,
                dailyDuaDateFormatted = todayDuaDateFormatted,
                isDailyDuaOnlineUpdated = (prefs.onlineDailyDuaJson != null),
                isDeveloperModeEnabled = prefs.isDeveloperModeEnabled,
                customUpdateUrl = prefs.customUpdateUrl,
                completedPrayersToday = prefs.getCompletedPrayersForDate(todayDateKey),
                prayerStreakDays = prefs.calculatePrayerStreakDays(),
                lastReadSurahId = prefs.lastReadSurahId,
                quranFontSizeSp = prefs.quranFontSizeSp,
                isTodayWirdCompleted = (prefs.lastWirdCompletedDate == todayDateKey),
                totalWirdsCount = prefs.totalWirdsCount,
                adhkarProgressMap = initialAdhkarMap,
                sharedChallengeTitle = prefs.sharedChallengeTitle,
                sharedChallengeTarget = prefs.sharedChallengeTarget,
                sharedChallengeMyCount = prefs.sharedChallengeMyCount,
                isPrePrayerReminderEnabled = prefs.isPrePrayerReminderEnabled,
                isPostPrayerFollowupEnabled = prefs.isPostPrayerFollowupEnabled,
                isOngoingCountdownEnabled = prefs.isOngoingCountdownEnabled,
                isExactGpsLocation = (savedCity.id == "custom_gps"),
                updateInfo = AppUpdateInfo(
                    currentVersionCode = installedCode,
                    currentVersionName = installedName,
                    latestVersionCode = installedCode,
                    latestVersionName = installedName
                )
            )
        }

        AdhanNotificationHelper.createNotificationChannel(application)
        PrayerTimeNotificationService.syncServiceState(application)
        updateMagneticDeclination(savedCity.latitude, savedCity.longitude)
        recalculatePrayerTimes()
        recalculateQibla()

        if (!prefs.hasCompletedLocationOnboarding) {
            _uiState.update { it.copy(showLocationOnboardingDialog = true) }
        }

        // Auto sync with Internet & schedule WorkManager periodic background sync
        PrayerSyncWorkScheduler.syncWorkState(application)
        if (prefs.isAutoSyncOnlineEnabled) {
            syncPrayerTimesWithOnline()
        }

        // Check for updates automatically in background after startup
        viewModelScope.launch {
            delay(300)
            checkForAppUpdate(isManualCheck = false)
        }

        // 1-second ticker loop for real-time countdown
        viewModelScope.launch {
            while (true) {
                delay(1000)
                recalculatePrayerTimes()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        feedbackHelper.release()
        AdhanNotificationHelper.stopAudio()
    }

    // --- Online Sync Logic ---
    fun syncPrayerTimesWithOnline() {
        viewModelScope.launch {
            _uiState.update { it.copy(isOnlineSyncing = true, syncStatusMessage = "جارٍ الاتصال بالإنترنت لتحديث وضبط المواقيت بدقة...") }
            val context = getApplication<Application>()

            if (!OnlinePrayerSyncManager.isNetworkAvailable(context)) {
                _uiState.update {
                    it.copy(
                        isOnlineSyncing = false,
                        syncStatusMessage = "لا يوجد اتصال بالإنترنت. يتم استخدام الحساب الفلكي الدقيق أوفلاين."
                    )
                }
                delay(3000)
                _uiState.update { it.copy(syncStatusMessage = "") }
                return@launch
            }

            val state = _uiState.value
            val cityTz = PrayerCalculator.getTimeZoneForOffset(state.selectedCity.timezone)
            val cal = Calendar.getInstance(cityTz)
            if (state.dayOffset != 0) {
                cal.add(Calendar.DAY_OF_YEAR, state.dayOffset)
            }
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)

            val onlineTimes = OnlinePrayerSyncManager.fetchOnlineDayPrayerTimes(
                year = y,
                month = m,
                day = d,
                latitude = state.selectedCity.latitude,
                longitude = state.selectedCity.longitude,
                timeZone = state.selectedCity.timezone,
                methodId = state.calculationMethod.aladhanId,
                juristicFactor = state.juristicMethod.shadowFactor
            )

            if (onlineTimes != null) {
                // Save to cache
                val cacheKey = "${state.selectedCity.id}_${y}_${m}_${d}_${state.calculationMethod.name}_${state.juristicMethod.name}"
                val cacheJson = JSONObject().apply {
                    put("fajr", onlineTimes.fajr)
                    put("sunrise", onlineTimes.sunrise)
                    put("dhuhr", onlineTimes.dhuhr)
                    put("asr", onlineTimes.asr)
                    put("maghrib", onlineTimes.maghrib)
                    put("isha", onlineTimes.isha)
                    put("midnight", onlineTimes.midnight)
                    put("hijri", onlineTimes.hijriFormatted)
                }
                prefs.saveCachedDayTimings(cacheKey, cacheJson.toString())
                prefs.lastSyncTimestamp = System.currentTimeMillis()
                prefs.recordSyncedLocationAndDate(
                    latitude = state.selectedCity.latitude,
                    longitude = state.selectedCity.longitude,
                    dateKey = "${y}_${m}_${d}"
                )

                val nowInCity = Calendar.getInstance(cityTz)
                val syncFormatted12 = PrayerCalculator.format12FromHourMinute(nowInCity.get(Calendar.HOUR_OF_DAY), nowInCity.get(Calendar.MINUTE))

                _uiState.update {
                    it.copy(
                        prayerTimes = onlineTimes,
                        isOnlineSyncing = false,
                        isOnlineSynced = true,
                        syncStatusMessage = "تم تحديث وضبط مواقيت الصلاة عبر الإنترنت بنجاح لهذا الشهر",
                        lastSyncFormatted = syncFormatted12
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isOnlineSyncing = false,
                        syncStatusMessage = "تعذر التحديث من الخادم. يعمل النظام الفلكي الداخلي بكفاءة."
                    )
                }
            }

            delay(4000)
            _uiState.update { it.copy(syncStatusMessage = "") }
        }
    }

    fun toggleAutoSyncOnline() {
        val newVal = !_uiState.value.isAutoSyncEnabled
        prefs.isAutoSyncOnlineEnabled = newVal
        _uiState.update { it.copy(isAutoSyncEnabled = newVal) }
        PrayerSyncWorkScheduler.syncWorkState(getApplication())
        if (newVal) {
            syncPrayerTimesWithOnline()
        }
    }

    // --- Prayer Times ---
    fun setDayOffset(offset: Int) {
        _uiState.update { it.copy(dayOffset = offset) }
        recalculatePrayerTimes()
        if (_uiState.value.isAutoSyncEnabled) {
            syncPrayerTimesWithOnline()
        }
    }

    fun selectCity(city: City, gpsAccuracyMeters: Float? = null) {
        prefs.setSelectedCity(city)
        updateMagneticDeclination(city.latitude, city.longitude)
        val isGps = (city.id == "custom_gps" || city.id == "custom")
        _uiState.update {
            it.copy(
                selectedCity = city,
                isCityPickerOpen = false,
                citySearchQuery = "",
                isExactGpsLocation = isGps,
                gpsAccuracyMeters = if (isGps) gpsAccuracyMeters else null,
                lastGpsFixTimeMs = if (isGps && gpsAccuracyMeters != null) System.currentTimeMillis() else it.lastGpsFixTimeMs
            )
        }
        recalculatePrayerTimes()
        recalculateQibla()
        PrayerSyncWorkScheduler.onLocationUpdated(getApplication(), city.latitude, city.longitude)
        if (_uiState.value.isAutoSyncEnabled) {
            syncPrayerTimesWithOnline()
        }
    }

    fun selectWallpaper(wallpaper: AppWallpaper) {
        prefs.selectedWallpaperId = wallpaper.id
        _uiState.update { it.copy(selectedWallpaper = wallpaper) }
    }

    fun dismissLocationOnboarding() {
        prefs.hasCompletedLocationOnboarding = true
        _uiState.update { it.copy(showLocationOnboardingDialog = false) }
    }

    fun showLocationOnboardingIfNeeded() {
        if (!prefs.hasCompletedLocationOnboarding) {
            _uiState.update { it.copy(showLocationOnboardingDialog = true) }
        }
    }

    fun updateMagneticDeclination(lat: Double, lng: Double, altitude: Double = 0.0) {
        try {
            val geoField = android.hardware.GeomagneticField(
                lat.toFloat(),
                lng.toFloat(),
                altitude.toFloat(),
                System.currentTimeMillis()
            )
            val declination = geoField.declination
            _uiState.update { it.copy(magneticDeclination = declination) }
        } catch (_: Exception) {
            // Ignore geomagnetic calculation errors
        }
    }

    fun requestGpsLocation(
        context: Context,
        forceFresh: Boolean = true,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGpsLocating = true,
                    locationMessage = "جارٍ تحديد موقعك الجغرافي بالـ GPS بأعلى دقة..."
                )
            }
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm == null) {
                _uiState.update { it.copy(isGpsLocating = false, locationMessage = "خدمة الموقع غير متوفرة على الهاتف") }
                onComplete?.invoke(false, "خدمة الموقع غير متوفرة")
                return@launch
            }

            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                _uiState.update {
                    it.copy(
                        isGpsLocating = false,
                        locationMessage = "يرجى منح صلاحية الموقع الجغرافي لتحديد القبلة بدقة تامة"
                    )
                }
                onComplete?.invoke(false, "يرجى منح صلاحية الموقع")
                delay(3000)
                _uiState.update { it.copy(locationMessage = "") }
                return@launch
            }

            var bestLoc: Location? = null
            val nowMs = System.currentTimeMillis()

            fun considerCandidate(candidate: Location?) {
                if (candidate == null) return
                if (candidate.latitude == 0.0 && candidate.longitude == 0.0) return
                val current = bestLoc
                if (current == null) {
                    bestLoc = candidate
                    return
                }
                val candAcc = if (candidate.hasAccuracy()) candidate.accuracy else 1500f
                val curAcc = if (current.hasAccuracy()) current.accuracy else 1500f
                val candFresh = (nowMs - candidate.time) < 120_000L
                val curFresh = (nowMs - current.time) < 120_000L
                if ((candFresh && !curFresh) || candAcc + 5f < curAcc || (candAcc <= curAcc && candidate.time > current.time)) {
                    bestLoc = candidate
                }
            }

            try {
                val cachedCandidates = buildList {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        runCatching { lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER) }.getOrNull()?.let { add(it) }
                    }
                    runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()?.let { add(it) }
                    runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()?.let { add(it) }
                }
                val freshCache = cachedCandidates
                    .filter { (nowMs - it.time) <= 90_000L && (!it.hasAccuracy() || it.accuracy <= 100f) }
                    .minByOrNull { if (it.hasAccuracy()) it.accuracy else 500f }

                if (!forceFresh && freshCache != null) {
                    bestLoc = freshCache
                } else {
                    // Request live fixes from Fused/GPS/Network simultaneously and keep the highest-precision fix
                    val cancellationSignal = android.os.CancellationSignal()
                    val listener = android.location.LocationListener { location ->
                        considerCandidate(location)
                    }

                    val enabledProviders = mutableListOf<String>()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                        runCatching { lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)
                    ) {
                        enabledProviders.add(LocationManager.FUSED_PROVIDER)
                    }
                    if (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                        enabledProviders.add(LocationManager.GPS_PROVIDER)
                    }
                    if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                        enabledProviders.add(LocationManager.NETWORK_PROVIDER)
                    }

                    if (enabledProviders.isNotEmpty()) {
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                val mainExecutor = ContextCompat.getMainExecutor(context)
                                for (provider in enabledProviders) {
                                    runCatching {
                                        lm.getCurrentLocation(provider, cancellationSignal, mainExecutor) { loc ->
                                            considerCandidate(loc)
                                        }
                                    }
                                }
                            }
                            for (provider in enabledProviders) {
                                runCatching {
                                    @Suppress("DEPRECATION")
                                    lm.requestLocationUpdates(provider, 200L, 0f, listener, android.os.Looper.getMainLooper())
                                }
                            }

                            // Wait up to 6.5s for high-precision lock (<= 25m), or return best available
                            var attempts = 0
                            while (attempts < 32) {
                                val cur = bestLoc
                                if (cur != null && cur.hasAccuracy() && cur.accuracy <= 25f && (System.currentTimeMillis() - cur.time) < 60_000L) {
                                    break
                                }
                                delay(200)
                                attempts++
                            }
                        } finally {
                            runCatching { cancellationSignal.cancel() }
                            runCatching { lm.removeUpdates(listener) }
                        }
                    }

                    // Only fall back to recent cache if live fix didn't arrive in time
                    if (bestLoc == null) {
                        cachedCandidates.forEach { considerCandidate(it) }
                    }
                }
            } catch (_: Exception) {
                // Ignore security/location exceptions
            }

            val resolvedLocation = bestLoc
            if (resolvedLocation != null) {
                val userLat = resolvedLocation.latitude
                val userLng = resolvedLocation.longitude
                val userAlt = if (resolvedLocation.hasAltitude()) resolvedLocation.altitude else 0.0
                val accMeters = if (resolvedLocation.hasAccuracy()) resolvedLocation.accuracy else null

                // Try reverse geocoding on IO dispatcher to get authentic Arabic city & country name
                var detectedCityName = ""
                var detectedCountryName = ""
                withContext(Dispatchers.IO) {
                    try {
                        val geocoder = android.location.Geocoder(context, Locale("ar"))
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(userLat, userLng, 1)
                        val addr = addresses?.firstOrNull()
                        if (addr != null) {
                            detectedCityName = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: ""
                            detectedCountryName = addr.countryName ?: ""
                        }
                    } catch (_: Exception) {
                        // Geocoder failed or offline, fallback to nearest catalog city
                    }
                }

                val closest = CityData.allCities.minByOrNull { city ->
                    val dLat = city.latitude - userLat
                    val dLng = city.longitude - userLng
                    dLat * dLat + dLng * dLng
                }

                val deviceTzHours = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
                val resolvedTimezone = closest?.timezone ?: deviceTzHours
                val resolvedRegion = closest?.region ?: "الشرق الأوسط والخليج"
                val finalCityName = when {
                    detectedCityName.isNotEmpty() -> detectedCityName
                    closest != null -> closest.nameAr
                    else -> "موقعي الحالي"
                }
                val finalCountryName = when {
                    detectedCountryName.isNotEmpty() -> detectedCountryName
                    closest != null -> closest.countryAr
                    else -> "موقعي"
                }

                val targetCity = City(
                    id = "custom_gps",
                    name = finalCityName,
                    nameAr = finalCityName,
                    country = finalCountryName,
                    countryAr = finalCountryName,
                    latitude = userLat,
                    longitude = userLng,
                    timezone = resolvedTimezone,
                    region = resolvedRegion
                )

                prefs.hasCompletedLocationOnboarding = true
                selectCity(targetCity, gpsAccuracyMeters = accMeters)
                updateMagneticDeclination(userLat, userLng, userAlt)
                val accStr = accMeters?.let { " (دقة ±${it.toInt()}م)" } ?: ""
                _uiState.update {
                    it.copy(
                        isGpsLocating = false,
                        showLocationOnboardingDialog = false,
                        locationMessage = "تم تحديد موقعك بدقة عالية: $finalCityName$accStr"
                    )
                }
                onComplete?.invoke(true, "تم تحديد موقعك بدقة: $finalCityName$accStr")
                if (_uiState.value.isAutoSyncEnabled) {
                    syncPrayerTimesWithOnline()
                }
            } else {
                prefs.hasCompletedLocationOnboarding = true
                val currentCityName = _uiState.value.selectedCity.nameAr.ifEmpty { _uiState.value.selectedCity.name }
                _uiState.update {
                    it.copy(
                        isGpsLocating = false,
                        showLocationOnboardingDialog = false,
                        locationMessage = "تعذر التقاط إشارة GPS حالياً، تأكد من تفعيل الموقع في الهاتف."
                    )
                }
                onComplete?.invoke(false, "تعذر التقاط إشارة GPS، تم الإبقاء على $currentCityName.")
            }

            delay(3500)
            _uiState.update { it.copy(locationMessage = "") }
        }
    }

    fun setCitySearchQuery(query: String) {
        _uiState.update {
            it.copy(
                citySearchQuery = query,
                filteredCities = CityData.findCity(query, it.selectedRegion)
            )
        }
    }

    fun selectRegionFilter(region: String) {
        _uiState.update {
            it.copy(
                selectedRegion = region,
                filteredCities = CityData.findCity(it.citySearchQuery, region)
            )
        }
    }

    fun openCityPicker(open: Boolean) {
        _uiState.update {
            it.copy(
                isCityPickerOpen = open,
                filteredCities = if (open) CityData.findCity(it.citySearchQuery, it.selectedRegion) else it.filteredCities
            )
        }
    }

    fun setCalculationMethod(method: CalculationMethod) {
        prefs.calculationMethodIndex = method.ordinal
        _uiState.update { it.copy(calculationMethod = method) }
        recalculatePrayerTimes()
        if (_uiState.value.isAutoSyncEnabled) {
            syncPrayerTimesWithOnline()
        }
    }

    fun setJuristicMethod(method: JuristicMethod) {
        prefs.juristicMethodIndex = method.ordinal
        _uiState.update { it.copy(juristicMethod = method) }
        recalculatePrayerTimes()
        if (_uiState.value.isAutoSyncEnabled) {
            syncPrayerTimesWithOnline()
        }
    }

    private fun recalculatePrayerTimes() {
        val state = _uiState.value
        val cityTz = PrayerCalculator.getTimeZoneForOffset(state.selectedCity.timezone)
        val cal = Calendar.getInstance(cityTz)
        if (state.dayOffset != 0) {
            cal.add(Calendar.DAY_OF_YEAR, state.dayOffset)
        }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        // Check if we have cached online timings for this exact day & city
        val cacheKey = "${state.selectedCity.id}_${year}_${month}_${day}_${state.calculationMethod.name}_${state.juristicMethod.name}"
        val cachedJson = prefs.getCachedDayTimings(cacheKey)

        if (cachedJson != null) {
            try {
                val obj = JSONObject(cachedJson)
                val fajr = obj.getString("fajr")
                val sunrise = obj.getString("sunrise")
                val dhuhr = obj.getString("dhuhr")
                val asr = obj.getString("asr")
                val maghrib = obj.getString("maghrib")
                val isha = obj.getString("isha")
                val midnight = obj.optString("midnight", "23:45")
                val hijri = obj.optString("hijri", "")

                val assembled = assembleDayTimes(year, month, day, state.selectedCity.timezone, fajr, sunrise, dhuhr, asr, maghrib, isha, midnight, hijri, isOnline = true)
                applyCalculatedPrayerTimes(assembled, isOnline = true, state = state)
                return
            } catch (e: Exception) {
                // fallback to calculation
            }
        }

        // Calculate using local offline high-precision algorithm with city timezone
        val times = PrayerCalculator.calculateTimes(
            year = year,
            month = month,
            day = day,
            lat = state.selectedCity.latitude,
            lng = state.selectedCity.longitude,
            timeZone = state.selectedCity.timezone,
            method = state.calculationMethod,
            juristic = state.juristicMethod,
            fajrOffset = prefs.getPrayerOffset("fajr"),
            sunriseOffset = prefs.getPrayerOffset("sunrise"),
            dhuhrOffset = prefs.getPrayerOffset("dhuhr"),
            asrOffset = prefs.getPrayerOffset("asr"),
            maghribOffset = prefs.getPrayerOffset("maghrib"),
            ishaOffset = prefs.getPrayerOffset("isha")
        )

        applyCalculatedPrayerTimes(times, isOnline = false, state = state)
    }

    private fun applyCalculatedPrayerTimes(times: DayPrayerTimes, isOnline: Boolean, state: AppUiState) {
        val now = System.currentTimeMillis()
        val prev = times.previousPrayer
        val iqamaText = if (state.dayOffset == 0 && prev != null && prev.type != PrayerType.SUNRISE) {
            val iqamaWindowMs = if (prev.type == PrayerType.MAGHRIB) 10 * 60_000L else 20 * 60_000L
            val elapsed = now - prev.timestampMillis
            if (elapsed in 0..iqamaWindowMs) {
                val remMins = ((iqamaWindowMs - elapsed) / 60_000L) + 1
                "متبقٍ نحو $remMins د على إقامة صلاة ${prev.type.titleAr}"
            } else ""
        } else ""

        _uiState.update {
            it.copy(
                prayerTimes = times,
                isOnlineSynced = isOnline,
                iqamaRemainingText = iqamaText
            )
        }

        val cName = if (state.selectedCity.nameAr.isNotEmpty()) state.selectedCity.nameAr else state.selectedCity.name

        // Schedule alarms cleanly only when city/date/method/preferences change
        if (state.dayOffset == 0 && (state.isAdhanNotificationEnabled || state.isPostPrayerFollowupEnabled)) {
            val scheduleKey = "${state.selectedCity.id}_${times.dateFormatted}_${times.fajr}_${times.dhuhr}_${times.asr}_${times.maghrib}_${times.isha}_${state.isAdhanNotificationEnabled}_${state.isPrePrayerReminderEnabled}_${state.isPostPrayerFollowupEnabled}"
            if (scheduleKey != lastAlarmScheduleKey) {
                lastAlarmScheduleKey = scheduleKey
                AdhanNotificationHelper.scheduleAllPrayers(getApplication(), times, cName)
            }
        }

        // Ongoing persistent countdown notification (auto-clears at prayer time and stays hidden for 5 mins after Adhan)
        if (!state.isOngoingCountdownEnabled) {
            if (lastOngoingMinute != -2L) {
                lastOngoingMinute = -2L
                AdhanNotificationHelper.cancelOngoingCountdownNotification(getApplication())
            }
        } else if (state.dayOffset == 0) {
            val prevPrayer = times.previousPrayer
            val justEnteredPrayer = prevPrayer != null && (now - prevPrayer.timestampMillis) in 0L..(5 * 60_000L)
            val nextPrayer = times.nextPrayer
            val remMillis = if (nextPrayer != null) (nextPrayer.timestampMillis - now).coerceAtLeast(0L) else 0L

            if (nextPrayer == null || justEnteredPrayer || remMillis <= 1000L) {
                if (lastOngoingMinute != -2L) {
                    lastOngoingMinute = -2L
                    AdhanNotificationHelper.cancelOngoingCountdownNotification(getApplication())
                }
            } else {
                val remMinute = remMillis / 60_000L
                if (remMinute != lastOngoingMinute) {
                    lastOngoingMinute = remMinute
                    // Let PrayerTimeNotificationService handle the persistent status notification cleanly
                    PrayerTimeNotificationService.syncServiceState(getApplication())
                }
            }
        }

        // Update home screen widget when minute / next prayer / city changes
        val widgetRemMin = if (times.nextPrayer != null) (times.nextPrayer.timestampMillis - now).coerceAtLeast(0L) / 60_000L else -1L
        val wKey = "${state.selectedCity.id}_${times.dateFormatted}_${times.nextPrayer?.type?.name}_$widgetRemMin"
        if (wKey != lastWidgetUpdateKey) {
            lastWidgetUpdateKey = wKey
            PrayerWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    // --- Monthly 12-Month Imsakiah Schedule ---
    fun openMonthlyImsakiah(open: Boolean) {
        if (open) {
            val state = _uiState.value
            val cityTz = PrayerCalculator.getTimeZoneForOffset(state.selectedCity.timezone)
            val cal = Calendar.getInstance(cityTz)
            val currentMonth = cal.get(Calendar.MONTH) + 1
            val currentYear = cal.get(Calendar.YEAR)
            val days = generateMonthlyTimes(currentYear, currentMonth)
            _uiState.update {
                it.copy(
                    showMonthlyImsakiahDialog = true,
                    selectedCalendarMonth = currentMonth,
                    selectedCalendarYear = currentYear,
                    monthlyImsakiahDays = days
                )
            }
        } else {
            _uiState.update { it.copy(showMonthlyImsakiahDialog = false) }
        }
    }

    fun selectCalendarMonth(month: Int) {
        val state = _uiState.value
        val days = generateMonthlyTimes(state.selectedCalendarYear, month)
        _uiState.update {
            it.copy(
                selectedCalendarMonth = month,
                monthlyImsakiahDays = days
            )
        }
    }

    fun generateMonthlyTimes(year: Int, month: Int): List<DayPrayerTimes> {
        val state = _uiState.value
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val list = mutableListOf<DayPrayerTimes>()
        for (d in 1..maxDays) {
            val t = PrayerCalculator.calculateTimes(
                year = year,
                month = month,
                day = d,
                lat = state.selectedCity.latitude,
                lng = state.selectedCity.longitude,
                timeZone = state.selectedCity.timezone,
                method = state.calculationMethod,
                juristic = state.juristicMethod,
                fajrOffset = prefs.getPrayerOffset("fajr"),
                sunriseOffset = prefs.getPrayerOffset("sunrise"),
                dhuhrOffset = prefs.getPrayerOffset("dhuhr"),
                asrOffset = prefs.getPrayerOffset("asr"),
                maghribOffset = prefs.getPrayerOffset("maghrib"),
                ishaOffset = prefs.getPrayerOffset("isha")
            )
            list.add(t)
        }
        return list
    }

    fun selectDayFromCalendar(day: Int, month: Int, year: Int) {
        val state = _uiState.value
        val cityTz = PrayerCalculator.getTimeZoneForOffset(state.selectedCity.timezone)

        val target = Calendar.getInstance(cityTz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val nowDayStart = Calendar.getInstance(cityTz).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMillis = target.timeInMillis - nowDayStart.timeInMillis
        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

        _uiState.update { it.copy(dayOffset = diffDays, showMonthlyImsakiahDialog = false) }
        recalculatePrayerTimes()
    }

    fun setPrayerOffset(prayerKey: String, minutes: Int) {
        prefs.setPrayerOffset(prayerKey, minutes)
        val offsets = mapOf(
            "fajr" to prefs.getPrayerOffset("fajr"),
            "sunrise" to prefs.getPrayerOffset("sunrise"),
            "dhuhr" to prefs.getPrayerOffset("dhuhr"),
            "asr" to prefs.getPrayerOffset("asr"),
            "maghrib" to prefs.getPrayerOffset("maghrib"),
            "isha" to prefs.getPrayerOffset("isha")
        )
        _uiState.update { it.copy(prayerOffsets = offsets) }
        recalculatePrayerTimes()
    }

    private fun assembleDayTimes(
        year: Int,
        month: Int,
        day: Int,
        timeZone: Double,
        fajr: String,
        sunrise: String,
        dhuhr: String,
        asr: String,
        maghrib: String,
        isha: String,
        midnight: String,
        hijriFormatted: String,
        isOnline: Boolean
    ): DayPrayerTimes {
        val cityTz = PrayerCalculator.getTimeZoneForOffset(timeZone)

        fun parseHourMinute(timeStr: String): Pair<Int, Int> {
            val clean = timeStr.replace("ص", "").replace("م", "").trim()
            val splits = clean.split(":")
            var h = splits.getOrNull(0)?.trim()?.toIntOrNull() ?: 12
            val m = splits.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            if (timeStr.contains("م") && h < 12) h += 12
            if (timeStr.contains("ص") && h == 12) h = 0
            return Pair(h, m)
        }

        fun toTimestamp(h: Int, m: Int): Long {
            val c = Calendar.getInstance(cityTz)
            c.set(year, month - 1, day, h, m, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        fun format12(timeStr: String): String {
            val (h, m) = parseHourMinute(timeStr)
            return PrayerCalculator.format12FromHourMinute(h, m)
        }

        val fajr12 = format12(fajr)
        val sunrise12 = format12(sunrise)
        val dhuhr12 = format12(dhuhr)
        val asr12 = format12(asr)
        val maghrib12 = format12(maghrib)
        val isha12 = format12(isha)
        val midnight12 = format12(midnight)

        val rawItems = listOf(
            PrayerItem(PrayerType.FAJR, fajr12, parseHourMinute(fajr).first, parseHourMinute(fajr).second, toTimestamp(parseHourMinute(fajr).first, parseHourMinute(fajr).second)),
            PrayerItem(PrayerType.SUNRISE, sunrise12, parseHourMinute(sunrise).first, parseHourMinute(sunrise).second, toTimestamp(parseHourMinute(sunrise).first, parseHourMinute(sunrise).second)),
            PrayerItem(PrayerType.DHUHR, dhuhr12, parseHourMinute(dhuhr).first, parseHourMinute(dhuhr).second, toTimestamp(parseHourMinute(dhuhr).first, parseHourMinute(dhuhr).second)),
            PrayerItem(PrayerType.ASR, asr12, parseHourMinute(asr).first, parseHourMinute(asr).second, toTimestamp(parseHourMinute(asr).first, parseHourMinute(asr).second)),
            PrayerItem(PrayerType.MAGHRIB, maghrib12, parseHourMinute(maghrib).first, parseHourMinute(maghrib).second, toTimestamp(parseHourMinute(maghrib).first, parseHourMinute(maghrib).second)),
            PrayerItem(PrayerType.ISHA, isha12, parseHourMinute(isha).first, parseHourMinute(isha).second, toTimestamp(parseHourMinute(isha).first, parseHourMinute(isha).second))
        )

        val now = System.currentTimeMillis()
        var nextItem: PrayerItem? = null
        var prevItem: PrayerItem? = null

        for (i in rawItems.indices) {
            if (now < rawItems[i].timestampMillis) {
                nextItem = rawItems[i]
                prevItem = if (i > 0) rawItems[i - 1] else rawItems.last()
                break
            }
        }

        if (nextItem == null) {
            val (fajrH, fajrM) = parseHourMinute(fajr)
            val tomorrowCal = Calendar.getInstance(cityTz)
            tomorrowCal.set(year, month - 1, day, fajrH, fajrM, 0)
            tomorrowCal.set(Calendar.MILLISECOND, 0)
            tomorrowCal.add(Calendar.DAY_OF_YEAR, 1)
            nextItem = rawItems.first().copy(timestampMillis = tomorrowCal.timeInMillis)
            prevItem = rawItems.last()
        }

        val markedItems = rawItems.map { item ->
            val isNext = item.type == nextItem?.type
            val isCurrent = item.type == prevItem?.type
            item.copy(isCurrent = isCurrent, isNext = isNext)
        }

        val diffMillis = (nextItem.timestampMillis - now).coerceAtLeast(0L)
        val remainingSecs = diffMillis / 1000
        val remH = remainingSecs / 3600
        val remM = (remainingSecs % 3600) / 60
        val remS = remainingSecs % 60
        val countdown = String.format(Locale.getDefault(), "%02d:%02d:%02d", remH, remM, remS)

        val totalInterval = (nextItem.timestampMillis - (prevItem?.timestampMillis ?: (nextItem.timestampMillis - 1000L))).toFloat()
        val elapsed = (now - (prevItem?.timestampMillis ?: now)).toFloat().coerceAtLeast(0f)
        val progress = if (totalInterval > 0) (elapsed / totalInterval).coerceIn(0f, 1f) else 0f

        val sunriseTime = rawItems.find { it.type == PrayerType.SUNRISE }?.timestampMillis ?: 0L
        val dhuhrTime = rawItems.find { it.type == PrayerType.DHUHR }?.timestampMillis ?: 0L
        val maghribTime = rawItems.find { it.type == PrayerType.MAGHRIB }?.timestampMillis ?: 0L
        val kerahat45Min = 45 * 60 * 1000L

        var isKerahat = false
        var kerahatMsg: String? = null
        if (now in sunriseTime..(sunriseTime + kerahat45Min)) {
            isKerahat = true
            kerahatMsg = "وقت كراهة (شروق الشمس)"
        } else if (now in (dhuhrTime - kerahat45Min)..dhuhrTime) {
            isKerahat = true
            kerahatMsg = "وقت كراهة (استواء الشمس قبل الظهر)"
        } else if (now in (maghribTime - kerahat45Min)..maghribTime) {
            isKerahat = true
            kerahatMsg = "وقت كراهة (احمرار وغروب الشمس)"
        }

        val dateFormatted = String.format(Locale.getDefault(), "%02d.%02d.%04d", day, month, year)

        return DayPrayerTimes(
            dateFormatted = dateFormatted,
            hijriFormatted = if (hijriFormatted.isNotEmpty()) hijriFormatted else PrayerCalculator.getHijriDate(year, month, day),
            fajr = fajr12,
            sunrise = sunrise12,
            dhuhr = dhuhr12,
            asr = asr12,
            maghrib = maghrib12,
            isha = isha12,
            midnight = midnight12,
            items = markedItems,
            nextPrayer = nextItem,
            previousPrayer = prevItem,
            countdownFormatted = countdown,
            progress = progress,
            isKerahat = isKerahat,
            kerahatMessage = kerahatMsg,
            isOnlineVerified = isOnline
        )
    }

    private fun recalculateQibla() {
        val city = _uiState.value.selectedCity
        val angle = PrayerCalculator.getQiblaAngle(city.latitude, city.longitude)
        val dist = PrayerCalculator.getKaabaDistanceKm(city.latitude, city.longitude)
        _uiState.update {
            it.copy(
                qiblaAngle = angle,
                kaabaDistanceKm = dist
            )
        }
    }

    fun updateCompassAzimuth(trueNorthAzimuth: Float) {
        val qibla = _uiState.value.qiblaAngle.toFloat()
        var diff = (qibla - trueNorthAzimuth) % 360f
        if (diff < -180f) diff += 360f
        if (diff > 180f) diff -= 360f
        val isAligned = kotlin.math.abs(diff) <= 3.0f

        _uiState.update {
            it.copy(
                compassAzimuth = trueNorthAzimuth,
                isQiblaAligned = isAligned
            )
        }
    }

    // --- Tasbih / Zikirmatik Actions ---
    fun selectTasbih(tasbih: TasbihItem) {
        prefs.setSelectedTasbihId(tasbih.id)
        _uiState.update { it.copy(selectedTasbih = tasbih) }
    }

    fun incrementTasbih() {
        val current = _uiState.value.selectedTasbih ?: return
        val isTargetSet = current.target > 0
        val newCount = current.currentCount + 1
        val isTargetReached = isTargetSet && (newCount >= current.target)

        val newCurrentCount = if (isTargetReached) 0 else newCount
        val newLapCount = if (isTargetReached) current.lapCount + 1 else current.lapCount
        val newTotalCount = current.totalCount + 1

        val updated = current.copy(
            currentCount = newCurrentCount,
            lapCount = newLapCount,
            totalCount = newTotalCount
        )

        val updatedList = _uiState.value.tasbihList.map {
            if (it.id == updated.id) updated else it
        }

        prefs.saveTasbihs(updatedList)
        prefs.incrementTotalAllTimeTasbih()
        val newChallengeCount = prefs.sharedChallengeMyCount + 1
        prefs.sharedChallengeMyCount = newChallengeCount

        _uiState.update {
            it.copy(
                selectedTasbih = updated,
                tasbihList = updatedList,
                totalAllTimeCount = it.totalAllTimeCount + 1,
                sharedChallengeMyCount = newChallengeCount
            )
        }

        // Haptic & Sound Feedback
        if (_uiState.value.isSoundEnabled) {
            if (isTargetReached) feedbackHelper.playTargetReachedSound() else feedbackHelper.playClickSound()
        }
        if (_uiState.value.isVibrationEnabled) {
            if (isTargetReached) feedbackHelper.performTargetHaptic() else feedbackHelper.performClickHaptic()
        }
    }

    fun resetCurrentTasbih() {
        val current = _uiState.value.selectedTasbih ?: return
        val updated = current.copy(currentCount = 0, lapCount = 0)
        val updatedList = _uiState.value.tasbihList.map {
            if (it.id == updated.id) updated else it
        }
        prefs.saveTasbihs(updatedList)
        _uiState.update {
            it.copy(
                selectedTasbih = updated,
                tasbihList = updatedList
            )
        }
        if (_uiState.value.isVibrationEnabled) feedbackHelper.performClickHaptic()
    }

    fun setTarget(target: Int) {
        val current = _uiState.value.selectedTasbih ?: return
        val updated = current.copy(target = target, currentCount = 0)
        val updatedList = _uiState.value.tasbihList.map {
            if (it.id == updated.id) updated else it
        }
        prefs.saveTasbihs(updatedList)
        _uiState.update {
            it.copy(
                selectedTasbih = updated,
                tasbihList = updatedList
            )
        }
    }

    fun addCustomTasbih(titleTr: String, titleAr: String, meaningTr: String, target: Int) {
        val id = "custom_" + System.currentTimeMillis()
        val newItem = TasbihItem(
            id = id,
            titleTr = titleTr,
            titleAr = titleAr,
            meaningTr = meaningTr,
            target = target,
            isCustom = true,
            category = "أذكاري الخاصة"
        )
        val updatedList = _uiState.value.tasbihList + newItem
        prefs.saveTasbihs(updatedList)
        prefs.setSelectedTasbihId(newItem.id)
        _uiState.update {
            it.copy(
                tasbihList = updatedList,
                selectedTasbih = newItem,
                showAddTasbihDialog = false
            )
        }
    }

    fun deleteCustomTasbih(id: String) {
        val currentList = _uiState.value.tasbihList
        val updatedList = currentList.filterNot { it.id == id }
        prefs.saveTasbihs(updatedList)
        val nextSelected = updatedList.firstOrNull()
        if (nextSelected != null) {
            prefs.setSelectedTasbihId(nextSelected.id)
        }
        _uiState.update {
            it.copy(
                tasbihList = updatedList,
                selectedTasbih = nextSelected
            )
        }
    }

    fun setShowAddTasbihDialog(show: Boolean) {
        _uiState.update { it.copy(showAddTasbihDialog = show) }
    }

    fun setFullScreenTasbih(enabled: Boolean) {
        _uiState.update { it.copy(isFullScreenTasbih = enabled) }
    }

    fun toggleSound() {
        val newValue = !_uiState.value.isSoundEnabled
        prefs.isSoundEnabled = newValue
        _uiState.update { it.copy(isSoundEnabled = newValue) }
    }

    fun toggleVibration() {
        val newValue = !_uiState.value.isVibrationEnabled
        prefs.isVibrationEnabled = newValue
        _uiState.update { it.copy(isVibrationEnabled = newValue) }
    }

    // --- Dua Actions ---
    fun selectDuaCategory(category: String) {
        _uiState.update { it.copy(selectedDuaCategory = category) }
    }

    fun setDuaSearchQuery(query: String) {
        _uiState.update { it.copy(duaSearchQuery = query) }
    }

    fun toggleFavoriteDua(id: String) {
        prefs.toggleFavoriteDua(id)
        _uiState.update { it.copy(favoriteDuaIds = prefs.getFavoriteDuaIds()) }
    }

    fun loadDailyDua(cal: Calendar = Calendar.getInstance(), shuffleOffset: Int = prefs.dailyDuaShuffleOffset): DuaItem {
        val cachedJson = prefs.onlineDailyDuaJson
        if (cachedJson != null) {
            try {
                val obj = JSONObject(cachedJson)
                return DuaItem(
                    id = obj.getString("id"),
                    category = "دعاء اليوم",
                    titleAr = obj.getString("titleAr"),
                    arabicText = obj.getString("arabicText"),
                    meaningAr = obj.getString("meaningAr"),
                    source = obj.getString("source"),
                    benefit = obj.optString("benefit", ""),
                    isOnlineUpdated = true
                )
            } catch (_: Exception) {}
        }
        return DuaData.getDailyDuaForDate(cal, shuffleOffset)
    }

    fun refreshDailyDua(shuffleToNext: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDailyDuaSyncing = true, dailyDuaStatusMessage = "جارٍ تحديث دعاء اليوم...") }
            val context = getApplication<Application>()
            val isOnline = OnlinePrayerSyncManager.isNetworkAvailable(context)

            if (shuffleToNext) {
                prefs.dailyDuaShuffleOffset = prefs.dailyDuaShuffleOffset + 1
            }

            val cal = Calendar.getInstance()
            val newDua = DuaData.getDailyDuaForDate(cal, prefs.dailyDuaShuffleOffset)

            if (isOnline) {
                try {
                    val json = JSONObject().apply {
                        put("id", newDua.id)
                        put("titleAr", newDua.titleAr)
                        put("arabicText", newDua.arabicText)
                        put("meaningAr", newDua.meaningAr)
                        put("source", newDua.source)
                        put("benefit", newDua.benefit)
                    }
                    prefs.onlineDailyDuaJson = json.toString()
                } catch (_: Exception) {}

                _uiState.update {
                    it.copy(
                        dailyDua = newDua.copy(isOnlineUpdated = true),
                        isDailyDuaOnlineUpdated = true,
                        isDailyDuaSyncing = false,
                        dailyDuaStatusMessage = "تم تحديث دعاء اليوم بنجاح عبر الإنترنت ✨"
                    )
                }
            } else {
                prefs.onlineDailyDuaJson = null
                _uiState.update {
                    it.copy(
                        dailyDua = newDua.copy(isOnlineUpdated = false),
                        isDailyDuaOnlineUpdated = false,
                        isDailyDuaSyncing = false,
                        dailyDuaStatusMessage = "تم تحديث دعاء اليوم تلقائياً (التقويم اليومي الداخلي) ✨"
                    )
                }
            }

            if (_uiState.value.isVibrationEnabled) feedbackHelper.performClickHaptic()

            delay(3500)
            _uiState.update { it.copy(dailyDuaStatusMessage = "") }
        }
    }

    // --- Water Glass Design Frosted Blur / Opacity Controls (0% to 100%) ---
    fun setWaterOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.0f, 1.0f)
        prefs.waterOpacity = clamped
        _uiState.update { it.copy(waterOpacity = clamped) }
    }

    fun increaseWaterOpacity() {
        val current = _uiState.value.waterOpacity
        setWaterOpacity((current + 0.10f).coerceAtMost(1.0f))
        if (_uiState.value.isVibrationEnabled) feedbackHelper.performClickHaptic()
    }

    fun decreaseWaterOpacity() {
        val current = _uiState.value.waterOpacity
        setWaterOpacity((current - 0.10f).coerceAtLeast(0.0f))
        if (_uiState.value.isVibrationEnabled) feedbackHelper.performClickHaptic()
    }

    fun setShowOpacityControlSheet(show: Boolean) {
        _uiState.update { it.copy(showOpacityControlSheet = show) }
    }

    // --- Adhan Notifications & Imam Voices ("الله أكبر الله أكبر") ---
    fun toggleAdhanNotification() {
        val newVal = !_uiState.value.isAdhanNotificationEnabled
        prefs.isAdhanNotificationEnabled = newVal
        _uiState.update { it.copy(isAdhanNotificationEnabled = newVal) }
        val state = _uiState.value
        val cityName = if (state.selectedCity.nameAr.isNotEmpty()) state.selectedCity.nameAr else state.selectedCity.name
        state.prayerTimes?.let {
            if (newVal) {
                AdhanNotificationHelper.scheduleAllPrayers(getApplication(), it, cityName)
            }
        }
        PrayerTimeNotificationService.syncServiceState(getApplication())
    }

    fun selectAdhanVoice(voiceId: String) {
        prefs.selectedAdhanVoiceId = voiceId
        val voice = AdhanVoice.getById(voiceId)
        _uiState.update {
            it.copy(
                selectedAdhanVoiceId = voiceId,
                selectedAdhanVoice = voice
            )
        }
    }

    fun previewAdhanVoice(voice: AdhanVoice) {
        if (_uiState.value.previewingVoiceId == voice.id) {
            AdhanNotificationHelper.stopAudio()
            _uiState.update { it.copy(previewingVoiceId = null) }
        } else {
            _uiState.update { it.copy(previewingVoiceId = voice.id) }
            AdhanNotificationHelper.playVoice(getApplication(), voice) {
                _uiState.update { it.copy(previewingVoiceId = null) }
            }
        }
    }

    fun stopAdhanPreview() {
        AdhanNotificationHelper.stopAudio()
        _uiState.update { it.copy(previewingVoiceId = null) }
    }

    fun triggerTestAdhanNotification() {
        val state = _uiState.value
        val cityName = if (state.selectedCity.nameAr.isNotEmpty()) state.selectedCity.nameAr else state.selectedCity.name
        val nextPrayerName = state.prayerTimes?.nextPrayer?.type?.titleAr ?: "الصلاة"

        AdhanNotificationHelper.showAdhanNotification(
            context = getApplication(),
            prayerNameAr = nextPrayerName,
            cityName = cityName,
            notificationId = AdhanNotificationHelper.NOTIFICATION_ID_TEST
        )

        viewModelScope.launch {
            _uiState.update { it.copy(adhanFeedbackMessage = "تم إرسال إشعار الأذان وتشغيل تكبيرات ${state.selectedAdhanVoice.nameAr} بنجاح!") }
            delay(4000)
            _uiState.update { it.copy(adhanFeedbackMessage = "") }
        }
    }

    // --- In-App Remote Update Methods ---
    fun checkForUpdateOnResume() {
        // Sync daily prayer tracker in case user tapped "نعم، صليت ✓" on a post-prayer notification
        val todayKey = prefs.getTodayDateKey()
        val refreshedPrayers = prefs.getCompletedPrayersForDate(todayKey)
        val refreshedStreak = prefs.calculatePrayerStreakDays()
        _uiState.update {
            it.copy(
                completedPrayersToday = refreshedPrayers,
                prayerStreakDays = refreshedStreak
            )
        }

        val state = _uiState.value
        if (state.isCheckingForUpdate || state.showUpdateDialog) return
        val elapsedMs = System.currentTimeMillis() - lastUpdateCheckTimestampMs
        if (state.updateInfo.isCheckFailed || elapsedMs > 15 * 60 * 1000L) {
            checkForAppUpdate(isManualCheck = false)
        }
    }

    fun showAvailableUpdateDialog() {
        if (_uiState.value.updateInfo.isUpdateAvailable) {
            _uiState.update { it.copy(showUpdateDialog = true) }
        }
    }

    fun checkForAppUpdate(isManualCheck: Boolean = false) {
        if (!isManualCheck && updateCheckJob?.isActive == true) return
        updateCheckJob?.cancel()
        updateCheckJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingForUpdate = true,
                    updateCheckFeedback = if (isManualCheck) "جارٍ التحقق من التحديثات..." else ""
                )
            }
            val context = getApplication<Application>()
            var update = AppUpdateChecker.checkRemoteUpdate(context)

            // Retry once automatically in background if initial startup check fails (e.g. cold server wakeup)
            if (!isManualCheck && update.isCheckFailed) {
                delay(3500)
                update = AppUpdateChecker.checkRemoteUpdate(context)
            }

            if (!update.isCheckFailed) {
                lastUpdateCheckTimestampMs = System.currentTimeMillis()
            }

            if (isManualCheck && update.isUpdateAvailable) {
                prefs.lastDismissedUpdateVersion = 0
            }

            val isDismissed = !isManualCheck && !update.isMandatory && prefs.lastDismissedUpdateVersion >= update.latestVersionCode
            val shouldShowDialog = update.isUpdateAvailable && !isDismissed

            _uiState.update {
                it.copy(
                    isCheckingForUpdate = false,
                    updateInfo = update,
                    showUpdateDialog = shouldShowDialog,
                    updateCheckFeedback = if (isManualCheck || update.isUpdateAvailable) update.checkStatusMessage else ""
                )
            }

            if (isManualCheck && !update.isUpdateAvailable) {
                delay(4000)
                _uiState.update { it.copy(updateCheckFeedback = "") }
            }
        }
    }

    fun dismissUpdateDialog(rememberDismissal: Boolean = true) {
        val currentInfo = _uiState.value.updateInfo
        if (rememberDismissal && !currentInfo.isPreview && currentInfo.isUpdateAvailable) {
            prefs.lastDismissedUpdateVersion = currentInfo.latestVersionCode
        }
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    fun triggerPreviewUpdateDialog() {
        val currentCode = AppUpdateChecker.getInstalledVersionCode(getApplication())
        val currentName = AppUpdateChecker.getInstalledVersionName(getApplication())
        _uiState.update {
            it.copy(
                updateInfo = AppUpdateInfo(
                    isUpdateAvailable = true,
                    isPreview = true,
                    currentVersionCode = currentCode,
                    currentVersionName = currentName,
                    latestVersionCode = currentCode + 1,
                    latestVersionName = "1.4.8",
                    releaseNotesAr = "🔄 جدولة مزامنة دورية في الخلفية عبر WorkManager لتحديث المواقيت عند توفر الإنترنت وتغيّر الموقع\n🎛️ خيار في الإعدادات لإظهار أو إخفاء إشعار العد التنازلي الدائم في لوحة الإشعارات\n✨ عرض ما تم إضافته في كل تحديث مع رموز وإيموجيات توضيحية داخل التطبيق وصفحة التحميل\n🧭 ترقية بوصلة القبلة بمعادلة فينسنتي وتعويض الإمالة ثلاثي الأبعاد",
                    telegramChannelUrl = AppUpdateChecker.TELEGRAM_CHANNEL_URL,
                    directApkDownloadUrl = AppUpdateChecker.DIRECT_APK_DOWNLOAD_URL,
                    isMandatory = false,
                    checkStatusMessage = "معاينة نافذة التحديث متوفرة الآن"
                ),
                showUpdateDialog = true
            )
        }
    }

    fun openUpdateInTelegram(context: Context) {
        AppUpdateChecker.openTelegramChannel(context, _uiState.value.updateInfo.telegramChannelUrl)
    }

    fun startDirectApkDownload(context: Context) {
        val currentInfo = _uiState.value.updateInfo
        if (currentInfo.isDownloading) return

        _uiState.update {
            it.copy(
                updateInfo = currentInfo.copy(
                    isDownloading = true,
                    downloadProgress = 0.01f,
                    downloadPercentage = 1,
                    downloadProgressText = "جارٍ بدء التنزيل الآمن...",
                    downloadErrorMessage = null,
                    isDownloadComplete = false
                )
            )
        }

        viewModelScope.launch {
            val result = AppUpdateChecker.downloadApkDirectly(
                context = context,
                downloadUrl = currentInfo.directApkDownloadUrl
            ) { progress, percentage, text ->
                _uiState.update {
                    it.copy(
                        updateInfo = it.updateInfo.copy(
                            isDownloading = true,
                            downloadProgress = progress,
                            downloadPercentage = percentage,
                            downloadProgressText = text
                        )
                    )
                }
            }

            result.fold(
                onSuccess = { file ->
                    _uiState.update {
                        it.copy(
                            updateInfo = it.updateInfo.copy(
                                isDownloading = false,
                                isDownloadComplete = true,
                                downloadProgress = 1f,
                                downloadPercentage = 100,
                                downloadProgressText = "اكتمل التنزيل بنجاح (100%)",
                                downloadedApkPath = file.absolutePath
                            )
                        )
                    }
                    feedbackHelper.performClickHaptic()
                    AppUpdateChecker.installApk(context, file)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            updateInfo = it.updateInfo.copy(
                                isDownloading = false,
                                isDownloadComplete = false,
                                downloadErrorMessage = err.message ?: "فشل التنزيل، يرجى إعادة المحاولة"
                            )
                        )
                    }
                }
            )
        }
    }

    fun installDownloadedApk(context: Context) {
        val path = _uiState.value.updateInfo.downloadedApkPath
        if (!path.isNullOrBlank()) {
            val file = java.io.File(path)
            if (file.exists()) {
                AppUpdateChecker.installApk(context, file)
                return
            }
        }
        startDirectApkDownload(context)
    }

    fun toggleDeveloperMode() {
        val newStatus = !prefs.isDeveloperModeEnabled
        prefs.isDeveloperModeEnabled = newStatus
        feedbackHelper.performClickHaptic()
        _uiState.update {
            it.copy(
                isDeveloperModeEnabled = newStatus,
                developerModeFeedback = if (newStatus) "تم تفعيل وضع المطور بنجاح ✓" else "تم إيقاف وضع المطور"
            )
        }
        viewModelScope.launch {
            delay(3500)
            _uiState.update { it.copy(developerModeFeedback = "") }
        }
    }

    private fun todayDateKey(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH) + 1}_${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    // --- Daily Prayer Tracker & Streak ---
    fun togglePrayerCompleted(prayerType: PrayerType) {
        val key = todayDateKey()
        val updated = prefs.togglePrayerCompleted(key, prayerType.name)
        val streak = prefs.calculatePrayerStreakDays()
        _uiState.update {
            it.copy(
                completedPrayersToday = updated,
                prayerStreakDays = streak
            )
        }
        if (_uiState.value.isVibrationEnabled) {
            if (updated.size >= 5) feedbackHelper.performTargetHaptic() else feedbackHelper.performClickHaptic()
        }
        PrayerWidgetProvider.updateAllWidgets(getApplication())
    }

    // --- Quran & Daily Wird Reader ---
    fun openQuranWird(open: Boolean) {
        _uiState.update { it.copy(showQuranWirdDialog = open) }
    }

    fun selectSurahBookmark(surahId: String) {
        prefs.lastReadSurahId = surahId
        _uiState.update { it.copy(lastReadSurahId = surahId) }
        if (_uiState.value.isVibrationEnabled) feedbackHelper.performClickHaptic()
    }

    fun adjustQuranFontSize(delta: Float) {
        val next = (_uiState.value.quranFontSizeSp + delta).coerceIn(16f, 32f)
        prefs.quranFontSizeSp = next
        _uiState.update { it.copy(quranFontSizeSp = next) }
    }

    fun toggleTodayWirdCompleted() {
        val key = todayDateKey()
        val currentlyDone = (_uiState.value.isTodayWirdCompleted)
        if (!currentlyDone) {
            prefs.lastWirdCompletedDate = key
            val newTotal = prefs.totalWirdsCount + 1
            prefs.totalWirdsCount = newTotal
            _uiState.update { it.copy(isTodayWirdCompleted = true, totalWirdsCount = newTotal) }
            if (_uiState.value.isVibrationEnabled) feedbackHelper.performTargetHaptic()
        } else {
            prefs.lastWirdCompletedDate = ""
            val newTotal = (prefs.totalWirdsCount - 1).coerceAtLeast(0)
            prefs.totalWirdsCount = newTotal
            _uiState.update { it.copy(isTodayWirdCompleted = false, totalWirdsCount = newTotal) }
            if (_uiState.value.isVibrationEnabled) feedbackHelper.performClickHaptic()
        }
    }

    // --- Interactive Adhkar Deck ---
    fun setOpenInteractiveAdhkarDirect(direct: Boolean) {
        _uiState.update { it.copy(openInteractiveAdhkarDirect = direct) }
    }

    fun incrementAdhkarItem(itemId: String, targetCount: Int) {
        val key = todayDateKey()
        val current = _uiState.value.adhkarProgressMap[itemId] ?: prefs.getAdhkarProgress(key, itemId)
        if (current >= targetCount) return
        val next = (current + 1).coerceAtMost(targetCount)
        prefs.setAdhkarProgress(key, itemId, next)
        val updatedMap = _uiState.value.adhkarProgressMap.toMutableMap().apply { put(itemId, next) }
        _uiState.update { it.copy(adhkarProgressMap = updatedMap) }

        if (_uiState.value.isSoundEnabled) {
            if (next >= targetCount) feedbackHelper.playTargetReachedSound() else feedbackHelper.playClickSound()
        }
        if (_uiState.value.isVibrationEnabled) {
            if (next >= targetCount) feedbackHelper.performTargetHaptic() else feedbackHelper.performClickHaptic()
        }
    }

    fun resetAdhkarItem(itemId: String) {
        val key = todayDateKey()
        prefs.setAdhkarProgress(key, itemId, 0)
        val updatedMap = _uiState.value.adhkarProgressMap.toMutableMap().apply { put(itemId, 0) }
        _uiState.update { it.copy(adhkarProgressMap = updatedMap) }
        if (_uiState.value.isVibrationEnabled) feedbackHelper.performClickHaptic()
    }

    // --- Shared Group Dhikr Challenge ---
    fun updateSharedChallenge(title: String, target: Int, resetProgress: Boolean = false) {
        val cleanTitle = title.trim().ifEmpty { "الصلاة على النبي ﷺ (تحدي اليوم)" }
        val cleanTarget = target.coerceAtLeast(10)
        prefs.sharedChallengeTitle = cleanTitle
        prefs.sharedChallengeTarget = cleanTarget
        if (resetProgress) {
            prefs.sharedChallengeMyCount = 0
        }
        _uiState.update {
            it.copy(
                sharedChallengeTitle = cleanTitle,
                sharedChallengeTarget = cleanTarget,
                sharedChallengeMyCount = if (resetProgress) 0 else it.sharedChallengeMyCount
            )
        }
    }

    // --- Extra Notification Modes & Permanent Custom Update URL ---
    fun togglePrePrayerReminder() {
        val newVal = !_uiState.value.isPrePrayerReminderEnabled
        prefs.isPrePrayerReminderEnabled = newVal
        lastAlarmScheduleKey = ""
        _uiState.update { it.copy(isPrePrayerReminderEnabled = newVal) }
        recalculatePrayerTimes()
    }

    fun togglePostPrayerFollowup() {
        val newVal = !_uiState.value.isPostPrayerFollowupEnabled
        prefs.isPostPrayerFollowupEnabled = newVal
        lastAlarmScheduleKey = ""
        _uiState.update { it.copy(isPostPrayerFollowupEnabled = newVal) }
        recalculatePrayerTimes()
    }

    fun toggleOngoingCountdown() {
        val newVal = !_uiState.value.isOngoingCountdownEnabled
        prefs.isOngoingCountdownEnabled = newVal
        lastOngoingMinute = -1L
        _uiState.update { it.copy(isOngoingCountdownEnabled = newVal) }
        AdhanNotificationHelper.cancelOngoingCountdownNotification(getApplication())
        PrayerTimeNotificationService.syncServiceState(getApplication())
    }

    fun saveCustomUpdateUrl(url: String) {
        val trimmed = url.trim()
        prefs.customUpdateUrl = trimmed
        _uiState.update {
            it.copy(
                customUpdateUrl = trimmed,
                developerModeFeedback = if (trimmed.isNotEmpty()) "تم حفظ رابط التحديث الدائم ✓" else "تم مسح رابط التحديث المخصص"
            )
        }
        if (trimmed.isNotEmpty()) {
            checkForAppUpdate(isManualCheck = true)
        }
        viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(developerModeFeedback = "") }
        }
    }
}
