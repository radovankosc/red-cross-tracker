package com.radovankosc.ridelog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.radovankosc.ridelog.ui.AppNav
import com.radovankosc.ridelog.ui.RideLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RideLogTheme {
                AppNav()
            }
        }
    }
}
