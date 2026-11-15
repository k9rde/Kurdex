package com.example.util

import com.example.model.CalculationMethod
import com.example.model.DayPrayerTimes
import com.example.model.JuristicMethod
import com.example.model.PrayerItem
import com.example.model.PrayerType
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.*

object PrayerCalculator {

    private fun d2r(d: Double) = d * Math.PI / 180.0
    private fun r2d(r: Double) = r * 180.0 / Math.PI
    private fun fixHour(a: Double): Double {
        var h = a - 24.0 * floor(a / 24.0)
        if (h < 0) h += 24.0
        return h
    }
    private fun fixAngle(a: Double): Double {
        var ang = a - 360.0 * floor(a / 360.0)
        if (ang < 0) ang += 360.0
        return ang
    }

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(d2r(g)) + 0.020 * sin(d2r(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(r2d(atan2(cos(d2r(e)) * sin(d2r(l)), cos(d2r(l)))) / 15.0)
        val dcl = r2d(asin(sin(d2r(e)) * sin(d2r(l))))
        val eqT = q / 15.0 - ra
        return Pair(dcl, eqT)
    }

    private fun hourAngle(altitude: Double, latitude: Double, declination: Double): Double {
        val cosHA = (sin(d2r(altitude)) - sin(d2r(latitude)) * sin(d2r(declination))) /
                (cos(d2r(latitude)) * cos(declination.let { d2r(it) }))
        val clamped = cosHA.coerceIn(-1.0, 1.0)
        return r2d(acos(clamped)) / 15.0
    }

    private fun asrHourAngle(factor: Double, latitude: Double, declination: Double): Double {
        val diff = abs(latitude - declination)
        val alt = r2d(atan(1.0 / (factor + tan(d2r(diff)))))
        return hourAngle(alt, latitude, declination)
    }

    fun getTimeZoneForOffset(offsetHours: Double): java.util.TimeZone {
        val totalMinutes = (offsetHours * 60).roundToInt()
        val sign = if (totalMinutes >= 0) "+" else "-"
        val absMins = abs(totalMinutes)
        val h = absMins / 60
        val m = absMins % 60
        val customId = String.format(Locale.US, "GMT%s%02d:%02d", sign, h, m)
        return java.util.TimeZone.getTimeZone(customId)
    }

    fun format12FromHourMinute(h24: Int, m: Int): String {
        val isPm = h24 >= 12
        val marker = if (isPm) "م" else "ص"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return String.format(Locale.getDefault(), "%02d:%02d %s", h12, m, marker)
    }

    fun calculateTimes(
        year: Int,
        month: Int,
        day: Int,
        lat: Double,
        lng: Double,
        timeZone: Double,
        method: CalculationMethod = CalculationMethod.MWL,
        juristic: JuristicMethod = JuristicMethod.STANDARD,
        fajrOffset: Int = 0,
        sunriseOffset: Int = 0,
        dhuhrOffset: Int = 0,
        asrOffset: Int = 0,
        maghribOffset: Int = 0,
        ishaOffset: Int = 0
    ): DayPrayerTimes {
        val jd = julianDay(year, month, day) - lng / (15.0 * 24.0)
        val (declination, eqt) = sunPosition(jd)

        val noon = fixHour(12.0 + timeZone - (lng / 15.0) - eqt)

        val sunriseHA = hourAngle(-0.8333, lat, declination)
        val fajrHA = hourAngle(-method.fajrAngle, lat, declination)
        val asrHA = asrHourAngle(juristic.shadowFactor, lat, declination)
        val sunsetHA = hourAngle(-0.8333, lat, declination)
        val ishaHA = if (method == CalculationMethod.MAKKAH) 0.0 else hourAngle(-method.ishaAngle, lat, declination)

        // Exact astronomical prayer hours without artificial delay buffers
        val fajrHours = fixHour(noon - fajrHA + fajrOffset / 60.0)
        val sunriseHours = fixHour(noon - sunriseHA + sunriseOffset / 60.0)
        val dhuhrHours = fixHour(noon + dhuhrOffset / 60.0)
        val asrHours = fixHour(noon + asrHA + asrOffset / 60.0)
        val maghribHours = fixHour(noon + sunsetHA + maghribOffset / 60.0)
        val ishaHours = if (method == CalculationMethod.MAKKAH) {
            fixHour(maghribHours + 1.5 + ishaOffset / 60.0) // 90 min after Maghrib
        } else {
            fixHour(noon + ishaHA + ishaOffset / 60.0)
        }
        val midnightHours = fixHour(maghribHours + fixHour(fajrHours + 24.0 - maghribHours) / 2.0)

        fun toHourMinute(decHour: Double): Pair<Int, Int> {
            val totalMins = (decHour * 60.0).roundToInt()
            val h = (totalMins / 60) % 24
            val m = totalMins % 60
            return Pair(h, m)
        }

        fun format12(decHour: Double): String {
            val (h, m) = toHourMinute(decHour)
            return format12FromHourMinute(h, m)
        }

        val cityTz = getTimeZoneForOffset(timeZone)

        fun toTimestamp(decHour: Double): Long {
            val (h, m) = toHourMinute(decHour)
            val c = Calendar.getInstance(cityTz)
            c.set(year, month - 1, day, h, m, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        val now = System.currentTimeMillis()

        val items = listOf(
            PrayerItem(PrayerType.FAJR, format12(fajrHours), toHourMinute(fajrHours).first, toHourMinute(fajrHours).second, toTimestamp(fajrHours)),
            PrayerItem(PrayerType.SUNRISE, format12(sunriseHours), toHourMinute(sunriseHours).first, toHourMinute(sunriseHours).second, toTimestamp(sunriseHours)),
            PrayerItem(PrayerType.DHUHR, format12(dhuhrHours), toHourMinute(dhuhrHours).first, toHourMinute(dhuhrHours).second, toTimestamp(dhuhrHours)),
            PrayerItem(PrayerType.ASR, format12(asrHours), toHourMinute(asrHours).first, toHourMinute(asrHours).second, toTimestamp(asrHours)),
            PrayerItem(PrayerType.MAGHRIB, format12(maghribHours), toHourMinute(maghribHours).first, toHourMinute(maghribHours).second, toTimestamp(maghribHours)),
            PrayerItem(PrayerType.ISHA, format12(ishaHours), toHourMinute(ishaHours).first, toHourMinute(ishaHours).second, toTimestamp(ishaHours))
        )

        // Find current and next prayer based on universal time now vs prayer timestamps
        var nextItem: PrayerItem? = null
        var prevItem: PrayerItem? = null

        for (i in items.indices) {
            if (now < items[i].timestampMillis) {
                nextItem = items[i]
                prevItem = if (i > 0) items[i - 1] else items.last()
                break
            }
        }

        // If after Isha, next is tomorrow's Fajr
        if (nextItem == null) {
            val (fajrH, fajrM) = toHourMinute(fajrHours)
            val tomorrowCal = Calendar.getInstance(cityTz)
            tomorrowCal.set(year, month - 1, day, fajrH, fajrM, 0)
            tomorrowCal.set(Calendar.MILLISECOND, 0)
            tomorrowCal.add(Calendar.DAY_OF_YEAR, 1)
            nextItem = items.first().copy(timestampMillis = tomorrowCal.timeInMillis)
            prevItem = items.last()
        }

        // Mark current and next
        val markedItems = items.map { item ->
            val isNext = item.type == nextItem?.type
            val isCurrent = item.type == prevItem?.type
            item.copy(isCurrent = isCurrent, isNext = isNext)
        }

        // Countdown & Progress
        val diffMillis = (nextItem.timestampMillis - now).coerceAtLeast(0L)
        val remainingSecs = diffMillis / 1000
        val remH = remainingSecs / 3600
        val remM = (remainingSecs % 3600) / 60
        val remS = remainingSecs % 60
        val countdown = String.format(Locale.getDefault(), "%02d:%02d:%02d", remH, remM, remS)

        val totalInterval = (nextItem.timestampMillis - (prevItem?.timestampMillis ?: (nextItem.timestampMillis - 1000L))).toFloat()
        val elapsed = (now - (prevItem?.timestampMillis ?: now)).toFloat().coerceAtLeast(0f)
        val progress = if (totalInterval > 0) (elapsed / totalInterval).coerceIn(0f, 1f) else 0f

        // Check Kerahat
        val sunriseTime = toTimestamp(sunriseHours)
        val dhuhrTime = toTimestamp(dhuhrHours)
        val maghribTime = toTimestamp(maghribHours)
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

        val dateStr = String.format(Locale.getDefault(), "%02d.%02d.%04d", day, month, year)
        val hijriStr = getHijriDate(year, month, day)

        return DayPrayerTimes(
            dateFormatted = dateStr,
            hijriFormatted = hijriStr,
            fajr = format12(fajrHours),
            sunrise = format12(sunriseHours),
            dhuhr = format12(dhuhrHours),
            asr = format12(asrHours),
            maghrib = format12(maghribHours),
            isha = format12(ishaHours),
            midnight = format12(midnightHours),
            items = markedItems,
            nextPrayer = nextItem,
            previousPrayer = prevItem,
            countdownFormatted = countdown,
            progress = progress,
            isKerahat = isKerahat,
            kerahatMessage = kerahatMsg
        )
    }

    private const val KAABA_LAT = 21.422487
    private const val KAABA_LNG = 39.826206

    private data class GeodesicResult(val forwardAzimuthDeg: Double, val distanceMeters: Double)

    private fun computeVincentyToKaaba(lat: Double, lng: Double): GeodesicResult {
        if (abs(lat - KAABA_LAT) < 1e-7 && abs(lng - KAABA_LNG) < 1e-7) {
            return GeodesicResult(0.0, 0.0)
        }

        // WGS84 ellipsoid constants
        val a = 6378137.0
        val f = 1.0 / 298.257223563
        val b = (1.0 - f) * a

        val phi1 = d2r(lat)
        val phi2 = d2r(KAABA_LAT)
        val lDiff = d2r(KAABA_LNG - lng)

        val tanU1 = (1.0 - f) * tan(phi1)
        val cosU1 = 1.0 / sqrt(1.0 + tanU1 * tanU1)
        val sinU1 = tanU1 * cosU1

        val tanU2 = (1.0 - f) * tan(phi2)
        val cosU2 = 1.0 / sqrt(1.0 + tanU2 * tanU2)
        val sinU2 = tanU2 * cosU2

        var lambda = lDiff
        var lambdaPrev: Double
        var sinLambda: Double
        var cosLambda: Double
        var sinSigma: Double
        var cosSigma: Double
        var sigma: Double
        var sinAlpha: Double
        var cosSqAlpha: Double
        var cos2SigmaM: Double
        var iterLimit = 100

        do {
            sinLambda = sin(lambda)
            cosLambda = cos(lambda)
            val term1 = cosU2 * sinLambda
            val term2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda
            sinSigma = sqrt(term1 * term1 + term2 * term2)
            if (sinSigma == 0.0) {
                return GeodesicResult(0.0, 0.0)
            }
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1.0 - sinAlpha * sinAlpha
            cos2SigmaM = if (cosSqAlpha != 0.0) cosSigma - 2.0 * sinU1 * sinU2 / cosSqAlpha else 0.0
            val cCoeff = (f / 16.0) * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha))
            lambdaPrev = lambda
            lambda = lDiff + (1.0 - cCoeff) * f * sinAlpha * (
                sigma + cCoeff * sinSigma * (
                    cos2SigmaM + cCoeff * cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)
                )
            )
        } while (abs(lambda - lambdaPrev) > 1e-12 && --iterLimit > 0)

        if (iterLimit == 0) {
            // Antipodal fallback to spherical great-circle
            val y = sin(lDiff) * cos(phi2)
            val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(lDiff)
            val bearing = fixAngle(r2d(atan2(y, x)))
            val dLat = phi2 - phi1
            val aHav = sin(dLat / 2).pow(2) + cos(phi1) * cos(phi2) * sin(lDiff / 2).pow(2)
            val cHav = 2.0 * atan2(sqrt(aHav), sqrt(1.0 - aHav))
            return GeodesicResult(bearing, 6371008.8 * cHav)
        }

        val uSq = cosSqAlpha * (a * a - b * b) / (b * b)
        val capA = 1.0 + uSq / 16384.0 * (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175.0 * uSq)))
        val capB = uSq / 1024.0 * (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)))
        val deltaSigma = capB * sinSigma * (
            cos2SigmaM + (capB / 4.0) * (
                cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM) -
                    (capB / 6.0) * cos2SigmaM * (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0 + 4.0 * cos2SigmaM * cos2SigmaM)
                )
            )
        val distanceMeters = b * capA * (sigma - deltaSigma)
        val alpha1 = atan2(cosU2 * sinLambda, cosU1 * sinU2 - sinU1 * cosU2 * cosLambda)
        return GeodesicResult(fixAngle(r2d(alpha1)), distanceMeters)
    }

    fun getQiblaAngle(lat: Double, lng: Double): Double {
        return computeVincentyToKaaba(lat, lng).forwardAzimuthDeg
    }

    fun getKaabaDistanceKm(lat: Double, lng: Double): Int {
        return (computeVincentyToKaaba(lat, lng).distanceMeters / 1000.0).roundToInt()
    }

    fun getHijriDate(year: Int, month: Int, day: Int): String {
        val jd = julianDay(year, month, day)
        val l = jd.toLong() - 1948440 + 10632
        val n = ((l - 1) / 10631).toInt()
        val lPrime = l - 10631 * n + 354
        val j = (((10985 - lPrime) / 5316).toInt()) * (((50 * lPrime) / 17719).toInt()) +
                ((lPrime / 5670).toInt()) * (((43 * lPrime) / 15238).toInt())
        val lDoublePrime = lPrime - (((30 - j) / 15).toInt()) * (((17719 * j) / 50).toInt()) -
                ((j / 16).toInt()) * (((15238 * j) / 43).toInt()) + 29
        val m = ((24 * lDoublePrime) / 709).toInt()
        val d = (lDoublePrime - ((709 * m) / 24).toInt()).toInt()
        val y = 30 * n + j - 30

        val hijriMonths = arrayOf(
            "المحرّم", "صفر", "ربيع الأول", "ربيع الآخر",
            "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
            "رمضان", "شوّال", "ذو القعدة", "ذو الحجة"
        )
        val monthIdx = (m - 1).coerceIn(0, 11)
        return "$d ${hijriMonths[monthIdx]} $y هـ"
    }
}
