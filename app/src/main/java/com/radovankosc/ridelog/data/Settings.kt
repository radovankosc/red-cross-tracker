package com.radovankosc.ridelog.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    val ratePerKm: Double = 0.40,
    val startFee: Double = 1.00,
    val driverName: String = "",
    val organization: String = "",
    val reportEmail: String = "",
    val mapsApiKey: String = "",
    /** Two-letter country code that address suggestions are limited to; blank means anywhere. */
    val regionCode: String = "sk",
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            ratePerKm = prefs.getString("ratePerKm", null)?.toDoubleOrNull() ?: d.ratePerKm,
            startFee = prefs.getString("startFee", null)?.toDoubleOrNull() ?: d.startFee,
            driverName = prefs.getString("driverName", d.driverName)!!,
            organization = prefs.getString("organization", d.organization)!!,
            reportEmail = prefs.getString("reportEmail", d.reportEmail)!!,
            mapsApiKey = prefs.getString("mapsApiKey", d.mapsApiKey)!!,
            regionCode = prefs.getString("regionCode", d.regionCode)!!,
        )
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("ratePerKm", s.ratePerKm.toString())
            .putString("startFee", s.startFee.toString())
            .putString("driverName", s.driverName)
            .putString("organization", s.organization)
            .putString("reportEmail", s.reportEmail)
            .putString("mapsApiKey", s.mapsApiKey)
            .putString("regionCode", s.regionCode)
            .apply()
        _state.value = s
    }
}
