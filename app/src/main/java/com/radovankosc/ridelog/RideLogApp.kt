package com.radovankosc.ridelog

import android.app.Application
import android.content.Context
import com.radovankosc.ridelog.data.AppDatabase
import com.radovankosc.ridelog.data.SettingsStore

class RideLogApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
}

val Context.app: RideLogApp get() = applicationContext as RideLogApp
