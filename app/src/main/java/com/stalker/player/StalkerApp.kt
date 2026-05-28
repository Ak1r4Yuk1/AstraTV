package com.stalker.player

import android.app.Application
import com.stalker.player.data.model.Strings

class StalkerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        Strings.lang.value = prefs.getString("language", "it") ?: "it"
    }
}