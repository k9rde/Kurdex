package com.example

import android.hardware.SensorManager
import android.view.Surface
import com.example.ui.screens.QiblaCompassMath
import com.example.util.PrayerCalculator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QiblaAccuracyTest {

    @Test
    fun vincentyQiblaBearings_matchRealWorldGeodesicStandards() {
        // Mosul, Iraq -> ~191.8 deg (SSW)
        val mosul = PrayerCalculator.getQiblaAngle(36.3400, 43.1300)
        assertTrue("Mosul Qibla expected ~191.8 deg, got $mosul", mosul in 191.0..192.6)

        // Baghdad, Iraq -> ~199.8 deg (SSW)
        val baghdad = PrayerCalculator.getQiblaAngle(33.3152, 44.3661)
        assertTrue("Baghdad Qibla expected ~199.8 deg, got $baghdad", baghdad in 199.0..200.6)

        // Cairo, Egypt -> ~136.1 deg (SE)
        val cairo = PrayerCalculator.getQiblaAngle(30.0444, 31.2357)
        assertTrue("Cairo Qibla expected ~136.1 deg, got $cairo", cairo in 135.3..137.0)

        // Riyadh, KSA -> ~244.6 deg (WSW)
        val riyadh = PrayerCalculator.getQiblaAngle(24.7136, 46.6753)
        assertTrue("Riyadh Qibla expected ~244.6 deg, got $riyadh", riyadh in 243.8..245.5)

        // London, UK -> ~119.0 deg (ESE)
        val london = PrayerCalculator.getQiblaAngle(51.5074, -0.1278)
        assertTrue("London Qibla expected ~119.0 deg, got $london", london in 118.2..119.8)

        // New York, USA -> ~58.5 deg (ENE)
        val ny = PrayerCalculator.getQiblaAngle(40.7128, -74.0060)
        assertTrue("New York Qibla expected ~58.5 deg, got $ny", ny in 57.7..59.3)

        // Sydney, Australia -> ~277.5 deg (W)
        val sydney = PrayerCalculator.getQiblaAngle(-33.8688, 151.2093)
        assertTrue("Sydney Qibla expected ~277.5 deg, got $sydney", sydney in 276.5..278.5)

        // Coincident Kaaba coordinates must not produce NaN
        val atKaaba = PrayerCalculator.getQiblaAngle(21.422487, 39.826206)
        assertFalse(atKaaba.isNaN())
        assertEquals(0, PrayerCalculator.getKaabaDistanceKm(21.422487, 39.826206))
    }

    @Test
    fun compassMath_flatAndSteepTiltRemainAccurateWithout180Flip() {
        // Construct Device->World rotation matrix for yaw clockwise from North and pitch upright toward user:
        // World X = East, World Y = North, World Z = Up
        fun buildRotationMatrix(yawDeg: Double, pitchUpDeg: Double): FloatArray {
            val psi = Math.toRadians(yawDeg)
            val theta = Math.toRadians(pitchUpDeg)
            val cosY = cos(psi).toFloat()
            val sinY = sin(psi).toFloat()
            val cosP = cos(theta).toFloat()
            val sinP = sin(theta).toFloat()

            // Device X (East when yaw=0) = (cosY, -sinY, 0)
            // Forward horizontal = (sinY, cosY, 0)
            // Device Y (tilted up by theta) = (sinY * cosP, cosY * cosP, sinP)
            // Device Z (screen normal facing user) = (-sinY * sinP, -cosY * sinP, cosP)
            return floatArrayOf(
                cosY, sinY * cosP, -sinY * sinP,
                -sinY, cosY * cosP, -cosY * sinP,
                0f, sinP, cosP
            )
        }

        // Flat facing East (90 deg)
        val flatEast = QiblaCompassMath.extractAzimuthAndTilt(buildRotationMatrix(90.0, 0.0), Surface.ROTATION_0)
        assertEquals(90f, flatEast.magneticAzimuthDeg, 0.5f)
        assertTrue(flatEast.isLevel)

        // Held upright tilted 70 degrees facing East (90 deg) - must stay 90 deg, not jump or flip!
        val uprightEast = QiblaCompassMath.extractAzimuthAndTilt(buildRotationMatrix(90.0, 70.0), Surface.ROTATION_0)
        assertEquals(90f, uprightEast.magneticAzimuthDeg, 0.8f)
        assertFalse(uprightEast.isLevel)

        // Held upright tilted 65 degrees facing South-Southwest (192 deg)
        val uprightQibla = QiblaCompassMath.extractAzimuthAndTilt(buildRotationMatrix(192.0, 65.0), Surface.ROTATION_0)
        assertEquals(192f, uprightQibla.magneticAzimuthDeg, 0.8f)

        // Landscape rotation 90 compensation
        val rotated = QiblaCompassMath.extractAzimuthAndTilt(buildRotationMatrix(270.0, 0.0), Surface.ROTATION_90)
        assertEquals(0f, rotated.magneticAzimuthDeg, 0.5f)
    }

    @Test
    fun compassMath_declinationSteeringAndInterferenceWorkProperly() {
        // Magnetic azimuth 355 with +5 East declination = 0 True North
        assertEquals(0f, QiblaCompassMath.magneticToTrueNorthDeg(355f, 5f), 0.01f)

        // Qibla 190, phone facing 170 -> +20 turn right
        assertEquals(20f, QiblaCompassMath.signedSteeringDiffDeg(190f, 170f), 0.01f)
        // Qibla 190, phone facing 210 -> -20 turn left
        assertEquals(-20f, QiblaCompassMath.signedSteeringDiffDeg(190f, 210f), 0.01f)

        // Earth magnetic field ~46 uT is clean; <18 or >72 is interference
        assertFalse(QiblaCompassMath.isMagneticInterference(46f))
        assertTrue(QiblaCompassMath.isMagneticInterference(12f))
        assertTrue(QiblaCompassMath.isMagneticInterference(95f))
    }
}
