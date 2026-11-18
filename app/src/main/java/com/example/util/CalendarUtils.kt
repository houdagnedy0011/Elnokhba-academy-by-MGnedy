package com.example.util

import android.os.Build
import com.example.model.CalendarMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.floor

object CalendarUtils {
    private val arabicLocale = Locale("ar", "EG")

    private val gregorianMonths = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    private val hijriMonths = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    fun todayDateKey(): String = dateKeyFromMillis(System.currentTimeMillis())

    fun currentMonthKey(): String = monthKeyFromMillis(System.currentTimeMillis())

    fun dateKeyFromMillis(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(millis))
    }

    fun monthKeyFromMillis(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        return sdf.format(Date(millis))
    }

    fun millisFromDateKey(dateKey: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.parse(dateKey)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun shiftDateKey(dateKey: String, deltaDays: Int): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millisFromDateKey(dateKey)
        cal.add(Calendar.DAY_OF_MONTH, deltaDays)
        return dateKeyFromMillis(cal.timeInMillis)
    }

    fun shiftMonthKey(monthKey: String, deltaMonths: Int): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val cal = Calendar.getInstance()
        cal.set(year, month, 1, 12, 0, 0)
        cal.add(Calendar.MONTH, deltaMonths)
        return monthKeyFromMillis(cal.timeInMillis)
    }

    fun formatGregorian(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthName = gregorianMonths.getOrElse(cal.get(Calendar.MONTH)) { "" }
        val year = cal.get(Calendar.YEAR)
        return "$day $monthName $year م"
    }

    fun formatHijri(millis: Long): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return try {
                val localDate = Instant.ofEpochMilli(millis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                val hijri = HijrahDate.from(localDate)
                val day = hijri.get(ChronoField.DAY_OF_MONTH)
                val monthIdx = hijri.get(ChronoField.MONTH_OF_YEAR) - 1
                val year = hijri.get(ChronoField.YEAR)
                "$day ${hijriMonths.getOrElse(monthIdx) { "" }} $year هـ"
            } catch (_: Exception) {
                formatHijriFallback(millis)
            }
        }
        return formatHijriFallback(millis)
    }

    // خوارزمية تحويل حسابية دقيقة للتقويم الهجري للأجهزة الأقدم من Android O
    private fun formatHijriFallback(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        val y = cal.get(Calendar.YEAR)
        val jd = if ((y > 1582) || (y == 1582 && m > 10) || (y == 1582 && m == 10 && d > 14)) {
            ((1461 * (y + 4800 + ((m - 14) / 12))) / 4) +
                ((367 * (m - 2 - 12 * ((m - 14) / 12))) / 12) -
                ((3 * (((y + 4900 + ((m - 14) / 12)) / 100))) / 4) +
                d - 32075
        } else {
            367 * y - ((7 * (y + 5001 + ((m - 9) / 7))) / 4) + ((275 * m) / 9) + d + 1729777
        }
        var l = jd - 1948440 + 10632
        val n = floor((l - 1) / 10631.0).toInt()
        l = l - 10631 * n + 354
        val j = ((floor((10985 - l) / 5316.0).toInt()) * (floor((50 * l) / 17719.0).toInt())) +
            ((floor(l / 5670.0).toInt()) * (floor((43 * l) / 15238.0).toInt()))
        l = l - ((floor((30 - j) / 15.0).toInt()) * (floor((17719 * j) / 50.0).toInt())) -
            ((floor(j / 16.0).toInt()) * (floor((15238 * j) / 43.0).toInt())) + 29
        val hMonth = floor((24 * l) / 709.0).toInt()
        val hDay = l - floor((709 * hMonth) / 24.0).toInt()
        val hYear = 30 * n + j - 30
        val monthName = hijriMonths.getOrElse(hMonth - 1) { "" }
        return "$hDay $monthName $hYear هـ"
    }

    fun formatDualDate(millis: Long, mode: CalendarMode = CalendarMode.GREGORIAN): String {
        val g = formatGregorian(millis)
        val h = formatHijri(millis)
        return if (mode == CalendarMode.HIJRI) "$h  |  $g" else "$g  |  $h"
    }

    fun formatDualDateFromKey(dateKey: String, mode: CalendarMode = CalendarMode.GREGORIAN): String =
        formatDualDate(millisFromDateKey(dateKey), mode)

    fun formatMonthLabel(monthKey: String, mode: CalendarMode = CalendarMode.GREGORIAN): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val cal = Calendar.getInstance().apply { set(year, month, 15, 12, 0, 0) }
        val gMonth = "${gregorianMonths.getOrElse(month) { "" }} $year م"
        val hParts = formatHijri(cal.timeInMillis).split(" ")
        val hLabel = if (hParts.size >= 4) "${hParts[1]} ${hParts[2]} هـ" else formatHijri(cal.timeInMillis)
        return if (mode == CalendarMode.HIJRI) "$hLabel ($gMonth)" else "$gMonth ($hLabel)"
    }

    fun formatCurrency(amount: Double, symbol: String = "ج.م"): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
            minimumFractionDigits = 0
        }
        return "${nf.format(amount)} $symbol"
    }
}
