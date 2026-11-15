package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.viewmodel.AppUiState
import com.example.viewmodel.AppViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal data class OrientationSample(
    val magneticAzimuthDeg: Float,
    val rollTiltNorm: Float,
    val pitchTiltNorm: Float,
    val isLevel: Boolean
)

internal object QiblaCompassMath {
    fun extractAzimuthAndTilt(
        rotationMatrix: FloatArray,
        displayRotation: Int = Surface.ROTATION_0
    ): OrientationSample {
        val orientationAngles = FloatArray(3)
        // Phone tilt from horizontal: |R[7]| >= sin(45 deg) indicates steep upright holding
        val isSteepTilt = abs(rotationMatrix[7]) >= 0.7071f

        if (isSteepTilt) {
            val remapped = FloatArray(9)
            // When top edge points up (R[7] >= 0), mapping device Y -> AXIS_Z aligns forward -Z (back camera) with heading
            val yAxis = if (rotationMatrix[7] >= 0f) SensorManager.AXIS_Z else SensorManager.AXIS_MINUS_Z
            val remappedOk = SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                yAxis,
                remapped
            )
            if (remappedOk) {
                SensorManager.getOrientation(remapped, orientationAngles)
            } else {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
            }
        } else {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
        }

        val rawDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val displayOffsetDeg = when (displayRotation) {
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 270f
            else -> 0f
        }
        val normalizedMagAzimuth = ((rawDeg + displayOffsetDeg) % 360f + 360f) % 360f

        // Compute normalized bubble offsets in [-1f..1f] (bubble floats toward raised edge)
        val pitchRad = asin((-rotationMatrix[7]).coerceIn(-1f, 1f))
        val rollRad = atan2(rotationMatrix[6], rotationMatrix[8])
        val maxTiltRad = Math.toRadians(35.0).toFloat()
        val pitchNorm = (pitchRad / maxTiltRad).coerceIn(-1f, 1f)
        val rollNorm = (rollRad / maxTiltRad).coerceIn(-1f, 1f)
        val totalTiltDeg = Math.toDegrees(sqrt((pitchRad * pitchRad + rollRad * rollRad).toDouble()))

        return OrientationSample(
            magneticAzimuthDeg = normalizedMagAzimuth,
            rollTiltNorm = rollNorm,
            pitchTiltNorm = pitchNorm,
            isLevel = totalTiltDeg <= 10.0
        )
    }

    fun magneticToTrueNorthDeg(magneticAzimuthDeg: Float, declinationDeg: Float): Float {
        return ((magneticAzimuthDeg + declinationDeg) % 360f + 360f) % 360f
    }

    fun signedSteeringDiffDeg(qiblaBearingDeg: Float, trueNorthAzimuthDeg: Float): Float {
        return ((qiblaBearingDeg - trueNorthAzimuthDeg + 540f) % 360f) - 180f
    }

    fun isMagneticInterference(microTesla: Float): Boolean {
        return microTesla > 0f && (microTesla < 18f || microTesla > 72f)
    }
}

@Composable
fun QiblaScreen(
    viewModel: AppViewModel,
    uiState: AppUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCompassSensor by remember { mutableStateOf(true) }
    var isLowAccuracy by remember { mutableStateOf(false) }
    var magneticFieldMicroTesla by remember { mutableFloatStateOf(0f) }
    var liveMagneticAzimuth by remember { mutableFloatStateOf(0f) }
    var rollTiltNorm by remember { mutableFloatStateOf(0f) }
    var pitchTiltNorm by remember { mutableFloatStateOf(0f) }
    var isPhoneLevel by remember { mutableStateOf(true) }
    val latestDeclination by rememberUpdatedState(uiState.magneticDeclination)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.requestGpsLocation(context, forceFresh = true)
        }
    }

    // Automatically ensure high-precision GPS lock when opening Qibla if permission already granted
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val isStaleOrDefault = !uiState.isExactGpsLocation ||
            (System.currentTimeMillis() - uiState.lastGpsFixTimeMs > 180_000L)
        if ((hasFine || hasCoarse) && isStaleOrDefault && !uiState.isGpsLocating) {
            viewModel.requestGpsLocation(context, forceFresh = false)
        }
    }

    // Dual-Sensor Tilt-Compensated Compass Listener + Magnetic Interference Monitor
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val currentDisplayRotation: () -> Int = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { context.display?.rotation }.getOrNull() ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation
                    ?: Surface.ROTATION_0
            }
        }

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val filteredAccel = FloatArray(3)
            private val filteredMag = FloatArray(3)
            private var accelSet = false
            private var magSet = false

            private var smoothSin = 0.0
            private var smoothCos = 0.0
            private var initialized = false
            private var lastVmSyncTime = 0L
            private var lastRotVectorTime = 0L

            private fun processSample(sample: OrientationSample) {
                val rad = Math.toRadians(sample.magneticAzimuthDeg.toDouble())
                val curSin = sin(rad)
                val curCos = cos(rad)

                if (!initialized) {
                    smoothSin = curSin
                    smoothCos = curCos
                    initialized = true
                } else {
                    val prevDeg = ((Math.toDegrees(atan2(smoothSin, smoothCos)) + 360.0) % 360.0).toFloat()
                    val stepDelta = abs(QiblaCompassMath.signedSteeringDiffDeg(sample.magneticAzimuthDeg, prevDeg))
                    // Fast alpha on intentional turns, tighter smoothing when steady to eliminate jitter
                    val alpha = if (stepDelta > 18f) 0.42 else 0.22
                    smoothSin += alpha * (curSin - smoothSin)
                    smoothCos += alpha * (curCos - smoothCos)
                }

                val smoothedRad = atan2(smoothSin, smoothCos)
                val smoothedDeg = ((Math.toDegrees(smoothedRad) + 360.0) % 360.0).toFloat()

                liveMagneticAzimuth = smoothedDeg
                rollTiltNorm = sample.rollTiltNorm
                pitchTiltNorm = sample.pitchTiltNorm
                isPhoneLevel = sample.isLevel

                val now = System.currentTimeMillis()
                if (now - lastVmSyncTime >= 120L) {
                    lastVmSyncTime = now
                    val trueAzimuth = QiblaCompassMath.magneticToTrueNorthDeg(
                        smoothedDeg,
                        latestDeclination
                    )
                    viewModel.updateCompassAzimuth(trueAzimuth)
                }
            }

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR,
                    Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        // If vendor HAL includes estimated heading accuracy (index 4 > 0.6 rad ~ 34 deg), mark low accuracy
                        if (event.values.size >= 5 && event.values[4] > 0.60f) {
                            isLowAccuracy = true
                        }
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        val sample = QiblaCompassMath.extractAzimuthAndTilt(
                            rotationMatrix = rotationMatrix,
                            displayRotation = currentDisplayRotation()
                        )
                        lastRotVectorTime = System.currentTimeMillis()
                        processSample(sample)
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val flux = sqrt(
                            event.values[0] * event.values[0] +
                                event.values[1] * event.values[1] +
                                event.values[2] * event.values[2]
                        )
                        magneticFieldMicroTesla = flux

                        if (!magSet) {
                            System.arraycopy(event.values, 0, filteredMag, 0, 3)
                            magSet = true
                        } else {
                            for (i in 0..2) {
                                filteredMag[i] = filteredMag[i] + 0.25f * (event.values[i] - filteredMag[i])
                            }
                        }

                        // Fallback if device lacks hardware rotation vector or it hasn't emitted recently
                        val useFallback = rotSensor == null || (System.currentTimeMillis() - lastRotVectorTime > 800L)
                        if (useFallback && accelSet && SensorManager.getRotationMatrix(rotationMatrix, null, filteredAccel, filteredMag)) {
                            processSample(
                                QiblaCompassMath.extractAzimuthAndTilt(
                                    rotationMatrix = rotationMatrix,
                                    displayRotation = currentDisplayRotation()
                                )
                            )
                        }
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        if (!accelSet) {
                            System.arraycopy(event.values, 0, filteredAccel, 0, 3)
                            accelSet = true
                        } else {
                            for (i in 0..2) {
                                filteredAccel[i] = filteredAccel[i] + 0.20f * (event.values[i] - filteredAccel[i])
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
                    sensor?.type == Sensor.TYPE_ROTATION_VECTOR ||
                    sensor?.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
                ) {
                    isLowAccuracy = (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                        accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW)
                }
            }
        }

        if (rotSensor != null) {
            sensorManager.registerListener(listener, rotSensor, SensorManager.SENSOR_DELAY_GAME)
            if (magneticSensor != null) {
                sensorManager.registerListener(listener, magneticSensor, SensorManager.SENSOR_DELAY_UI)
            }
            if (accelSensor != null) {
                sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            }
        } else if (magneticSensor != null && accelSensor != null) {
            sensorManager.registerListener(listener, magneticSensor, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            hasCompassSensor = false
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val qiblaAngle = uiState.qiblaAngle.toFloat()
    val declination = uiState.magneticDeclination
    val compassAzimuth = QiblaCompassMath.magneticToTrueNorthDeg(liveMagneticAzimuth, declination)
    val relativeAngle = (qiblaAngle - compassAzimuth + 360f) % 360f
    val angleDiff = QiblaCompassMath.signedSteeringDiffDeg(qiblaAngle, compassAzimuth)
    val absDiff = abs(angleDiff)
    val isAligned = hasCompassSensor && absDiff <= 2.5f
    val hasMagneticInterference = QiblaCompassMath.isMagneticInterference(magneticFieldMicroTesla)

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(isAligned) {
        if (isAligned) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Smooth shortest-path unwrapped dial & needle animations
    val animNeedle = remember { Animatable(relativeAngle) }
    LaunchedEffect(relativeAngle) {
        var diff = (relativeAngle - animNeedle.value) % 360f
        if (diff > 180f) diff -= 360f else if (diff < -180f) diff += 360f
        animNeedle.animateTo(
            targetValue = animNeedle.value + diff,
            animationSpec = tween(durationMillis = 100, easing = LinearOutSlowInEasing)
        )
    }

    val targetDial = -compassAzimuth
    val animDial = remember { Animatable(targetDial) }
    LaunchedEffect(targetDial) {
        var diff = (targetDial - animDial.value) % 360f
        if (diff > 180f) diff -= 360f else if (diff < -180f) diff += 360f
        animDial.animateTo(
            targetValue = animDial.value + diff,
            animationSpec = tween(durationMillis = 100, easing = LinearOutSlowInEasing)
        )
    }

    val dialBorderColor by animateColorAsState(
        targetValue = when {
            isAligned -> EmeraldLight
            hasMagneticInterference -> Color(0xFFEF4444)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(250),
        label = "dial_color"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            val cityName = if (uiState.selectedCity.nameAr.isNotEmpty()) uiState.selectedCity.nameAr else uiState.selectedCity.name
            val countryName = if (uiState.selectedCity.countryAr.isNotEmpty()) uiState.selectedCity.countryAr else uiState.selectedCity.country

            Text(
                text = "بوصلة القبلة المشرفة",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            // High-precision GPS Location Pill & Instant Refresh Trigger
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (uiState.isExactGpsLocation) Color(0x2E10B981) else Color(0x2ED4AF37),
                border = BorderStroke(
                    1.dp,
                    if (uiState.isExactGpsLocation) Color(0x7068D391) else Color(0x70F3D267)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !uiState.isGpsLocating) {
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            viewModel.requestGpsLocation(context, forceFresh = true)
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                    .testTag("qibla_refresh_gps_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.isGpsLocating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = GoldLight
                        )
                    } else {
                        Icon(
                            imageVector = if (uiState.isExactGpsLocation) Icons.Default.GpsFixed else Icons.Default.MyLocation,
                            contentDescription = "تحديث الموقع بالـ GPS",
                            tint = if (uiState.isExactGpsLocation) Color(0xFF86EFAC) else GoldLight,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Column {
                        val gpsLabel = when {
                            uiState.isGpsLocating -> "جارٍ تحديد إحداثياتك بالـ GPS..."
                            uiState.isExactGpsLocation && uiState.gpsAccuracyMeters != null ->
                                "$cityName • دقة GPS ±${uiState.gpsAccuracyMeters.roundToInt()}م (تحديث)"
                            uiState.isExactGpsLocation -> "$cityName • موقع GPS محفوظ (اضغط للتحديث)"
                            else -> "$cityName ($countryName) • اضغط لتحديد موقعك بالـ GPS"
                        }
                        Text(
                            text = gpsLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.4f° N, %.4f° E • الشمال الحقيقي",
                                uiState.selectedCity.latitude,
                                uiState.selectedCity.longitude
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xCCF3E5AB)
                        )
                    }
                }
            }

            if (uiState.locationMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.locationMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF86EFAC)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Alignment Status Banner with Physical Non-Mirrored Turn Icons
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when {
                    !hasCompassSensor -> Color(0x44EF4444)
                    isAligned -> EmeraldPrimary
                    else -> Color(0x550C251D)
                },
                border = BorderStroke(
                    1.dp,
                    if (isAligned) EmeraldLight else Color(0x44FFFFFF)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("qibla_alignment_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val statusIcon = when {
                        !hasCompassSensor -> Icons.Default.Warning
                        isAligned -> Icons.Default.CheckCircle
                        angleDiff > 0f -> Icons.Default.RotateRight
                        else -> Icons.Default.RotateLeft
                    }
                    val formattedQibla = String.format(Locale.US, "%.1f°", qiblaAngle)
                    val statusText = when {
                        !hasCompassSensor -> "الهاتف لا يحتوي على حساس بوصلة مغناطيسية — اتجه نحو الزاوية $formattedQibla"
                        isAligned -> "✨ أنت الآن باتجاه الكعبة المشرفة مباشرة! ($formattedQibla)"
                        angleDiff > 0f -> "أدِر الهاتف يميناً بمقدار ${angleDiff.roundToInt()}° نحو الكعبة"
                        else -> "أدِر الهاتف يساراً بمقدار ${(-angleDiff).roundToInt()}° نحو الكعبة"
                    }
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = if (isAligned) Color.White else GoldLight,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (hasMagneticInterference || isLowAccuracy) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasMagneticInterference) Color(0x44EF4444) else Color(0x33D4AF37),
                    border = BorderStroke(
                        1.dp,
                        if (hasMagneticInterference) Color(0x88F87171) else Color(0x66F3D267)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hasMagneticInterference) Color(0xFFFCA5A5) else GoldLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val warningMsg = if (hasMagneticInterference) {
                            "تداخل مغناطيسي (${magneticFieldMicroTesla.roundToInt()} µT)! أبعد الغطاء المغناطيسي أو المعادن"
                        } else {
                            "دقة الحساس منخفضة — حرّك الهاتف في الهواء على شكل رقم (8) للمعايرة"
                        }
                        Text(
                            text = warningMsg,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Precision Compass Dial with Built-In Bubble Level
            Box(
                modifier = Modifier
                    .size(284.dp)
                    .testTag("compass_dial_box"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xCC092018),
                    border = BorderStroke(3.dp, dialBorderColor),
                    shadowElevation = 10.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Rotating Geographic North Dial (True North Corrected)
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(animDial.value)
                        ) {
                            val radius = size.minDimension / 2 - 14.dp.toPx()
                            val center = Offset(size.width / 2, size.height / 2)

                            for (i in 0 until 72) {
                                val deg = i * 5.0
                                val rad = Math.toRadians(deg - 90.0)
                                val isMajor = (i % 18 == 0)
                                val isMinor = (i % 6 == 0)
                                val tickLength = if (isMajor) 14.dp.toPx() else if (isMinor) 8.dp.toPx() else 4.dp.toPx()
                                val strokeWidth = if (isMajor) 3.dp.toPx() else 1.4.dp.toPx()

                                val startX = center.x + (radius - tickLength) * cos(rad).toFloat()
                                val startY = center.y + (radius - tickLength) * sin(rad).toFloat()
                                val endX = center.x + radius * cos(rad).toFloat()
                                val endY = center.y + radius * sin(rad).toFloat()

                                val tickColor = if (isMajor && deg == 0.0) Color(0xFFEF4444) else Color.White.copy(alpha = 0.45f)
                                drawLine(
                                    color = tickColor,
                                    start = Offset(startX, startY),
                                    end = Offset(endX, endY),
                                    strokeWidth = strokeWidth
                                )
                            }

                            val northArrowPath = Path().apply {
                                moveTo(center.x, center.y - radius - 4.dp.toPx())
                                lineTo(center.x + 6.dp.toPx(), center.y - radius + 10.dp.toPx())
                                lineTo(center.x - 6.dp.toPx(), center.y - radius + 10.dp.toPx())
                                close()
                            }
                            drawPath(northArrowPath, color = Color(0xFFEF4444))

                            val textRadius = radius - 24.dp.toPx()
                            val cardPaint = Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 13.sp.toPx()
                                textAlign = Paint.Align.CENTER
                                isFakeBoldText = true
                                isAntiAlias = true
                            }
                            val northPaint = Paint().apply {
                                color = android.graphics.Color.parseColor("#EF4444")
                                textSize = 15.sp.toPx()
                                textAlign = Paint.Align.CENTER
                                isFakeBoldText = true
                                isAntiAlias = true
                            }

                            drawContext.canvas.nativeCanvas.drawText("ش", center.x, center.y - textRadius + 5.dp.toPx(), northPaint)
                            drawContext.canvas.nativeCanvas.drawText("ق", center.x + textRadius, center.y + 5.dp.toPx(), cardPaint)
                            drawContext.canvas.nativeCanvas.drawText("ج", center.x, center.y + textRadius + 6.dp.toPx(), cardPaint)
                            drawContext.canvas.nativeCanvas.drawText("غ", center.x - textRadius, center.y + 5.dp.toPx(), cardPaint)
                        }

                        // Rotating Kaaba Pointer Arrow
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(animNeedle.value)
                        ) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val pointerLength = size.minDimension / 2 - 32.dp.toPx()
                            val kaabaSize = 34.dp.toPx()
                            val kaabaTop = center.y - pointerLength

                            drawLine(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        if (isAligned) EmeraldLight else GoldAccent,
                                        if (isAligned) EmeraldPrimary else GoldLight.copy(alpha = 0.5f)
                                    ),
                                    startY = kaabaTop + kaabaSize,
                                    endY = center.y
                                ),
                                start = Offset(center.x, center.y),
                                end = Offset(center.x, kaabaTop + kaabaSize),
                                strokeWidth = 3.5.dp.toPx()
                            )

                            val arrowTipPath = Path().apply {
                                moveTo(center.x, kaabaTop - 8.dp.toPx())
                                lineTo(center.x + 9.dp.toPx(), kaabaTop)
                                lineTo(center.x - 9.dp.toPx(), kaabaTop)
                                close()
                            }
                            drawPath(arrowTipPath, if (isAligned) EmeraldLight else GoldAccent)

                            if (isAligned) {
                                drawRoundRect(
                                    color = EmeraldLight.copy(alpha = 0.45f),
                                    topLeft = Offset(center.x - kaabaSize / 2 - 5.dp.toPx(), kaabaTop - 5.dp.toPx()),
                                    size = Size(kaabaSize + 10.dp.toPx(), kaabaSize + 10.dp.toPx()),
                                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                )
                            }

                            drawRoundRect(
                                color = Color(0xFF141414),
                                topLeft = Offset(center.x - kaabaSize / 2, kaabaTop),
                                size = Size(kaabaSize, kaabaSize),
                                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                            )

                            drawRoundRect(
                                color = GoldAccent,
                                topLeft = Offset(center.x - kaabaSize / 2, kaabaTop),
                                size = Size(kaabaSize, kaabaSize),
                                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx())
                            )

                            val bandHeight = kaabaSize * 0.22f
                            val bandTop = kaabaTop + kaabaSize * 0.18f
                            drawRect(
                                color = GoldAccent,
                                topLeft = Offset(center.x - kaabaSize / 2 + 2.dp.toPx(), bandTop),
                                size = Size(kaabaSize - 4.dp.toPx(), bandHeight)
                            )

                            val doorWidth = kaabaSize * 0.28f
                            val doorHeight = kaabaSize * 0.45f
                            val doorLeft = center.x - kaabaSize / 2 + kaabaSize * 0.52f
                            val doorTop = kaabaTop + kaabaSize * 0.48f
                            drawRoundRect(
                                color = GoldLight,
                                topLeft = Offset(doorLeft, doorTop),
                                size = Size(doorWidth, doorHeight),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )

                            val kaabaTextPaint = Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 10.sp.toPx()
                                textAlign = Paint.Align.CENTER
                                isFakeBoldText = true
                                isAntiAlias = true
                            }
                            drawContext.canvas.nativeCanvas.drawText("الكعبة", center.x, kaabaTop + kaabaSize + 13.dp.toPx(), kaabaTextPaint)
                        }

                        // Built-In Spirit Bubble Level Pivot in the Center
                        Canvas(modifier = Modifier.size(48.dp)) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val outerR = size.minDimension / 2f - 2.dp.toPx()
                            drawCircle(
                                color = Color(0xCC061711),
                                radius = outerR,
                                center = center
                            )
                            drawCircle(
                                color = if (isPhoneLevel) Color(0xAA68D391) else Color(0x88F3D267),
                                radius = outerR,
                                center = center,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                            val maxOffset = outerR - 7.dp.toPx()
                            val bubbleCenter = Offset(
                                x = center.x + (rollTiltNorm * maxOffset),
                                y = center.y + (pitchTiltNorm * maxOffset)
                            )
                            drawCircle(
                                color = if (isPhoneLevel) EmeraldLight else GoldAccent,
                                radius = 6.dp.toPx(),
                                center = bubbleCenter
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.7f),
                                radius = 2.dp.toPx(),
                                center = center
                            )
                        }

                        // Top Fixed Phone Lubber Line Pointer
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isAligned) EmeraldLight else GoldLight)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Precision Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x880C251D)),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "زاوية القبلة الدقيقة",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xCCFFFFFF)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f°", qiblaAngle),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                        Text(
                            text = "اتجاهك الآن: ${compassAzimuth.roundToInt()}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF86EFAC)
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x880C251D)),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "المسافة إلى الكعبة",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xCCFFFFFF)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${uiState.kaabaDistanceKm} كم",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        val magText = if (magneticFieldMicroTesla > 0f) {
                            "${magneticFieldMicroTesla.roundToInt()} µT • انحراف ${String.format(Locale.US, "%+.1f°", declination)}"
                        } else {
                            "انحراف مغناطيسي ${String.format(Locale.US, "%+.1f°", declination)}"
                        }
                        Text(
                            text = magText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xCCF3E5AB)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Geodesic & Calibration Note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x66092018),
                border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 108.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = GoldLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "محسوبة بمعادلة فينسنتي الجيوديسية (WGS84) ومصححة للشمال الجغرافي الحقيقي وتعويض إمالة الهاتف. ضع النقطة وسط الدائرة لأعلى دقة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xE6FFFFFF)
                    )
                }
            }
        }
    }
}
