package com.radovankosc.ridelog.data

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

private val SK = Locale.forLanguageTag("sk-SK")
private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

object Fmt {
    fun date(d: LocalDate): String = d.format(DATE)
    fun date(epochDay: Long): String = date(LocalDate.ofEpochDay(epochDay))
    fun money(v: Double): String = String.format(SK, "%.2f €", v)
    fun km(v: Double): String = String.format(SK, "%.1f km", v)
    /** A number for an input field, without trailing ",0". */
    fun plain(v: Double): String = String.format(SK, "%.2f", v).trimEnd('0').trimEnd(',')
    fun receiptNo(r: Ride): String = "${r.receiptNumber}/${r.receiptYear}"
    fun trip(r: Ride): String = if (r.roundTrip) "Tam a späť" else "Jednosmerne"

    /** Accepts both "12,5" and "12.5". */
    fun parse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
}

fun round1(v: Double): Double = (v * 10).roundToLong() / 10.0
fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0

/** Lowercase without accents, so "stefan" finds "Štefan". */
fun searchKey(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().trim()

object Pricing {
    fun totalKm(oneWayKm: Double, roundTrip: Boolean): Double = round1(if (roundTrip) oneWayKm * 2 else oneWayKm)
    fun price(totalKm: Double, ratePerKm: Double, startFee: Double): Double = round2(startFee + totalKm * ratePerKm)
}
