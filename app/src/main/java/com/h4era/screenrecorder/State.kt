package com.h4era.screenrecorder

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object RecorderState {
    var recording by mutableStateOf(false)
    var paused by mutableStateOf(false)
    var elapsedSec by mutableStateOf(0L)
    var info by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    var lastSaved by mutableStateOf<Uri?>(null)
    var libraryVersion by mutableStateOf(0)
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("h4era", Context.MODE_PRIVATE)

    fun height(c: Context): Int = sp(c).getInt("height", 720)
    fun fps(c: Context): Int = sp(c).getInt("fps", 30)
    fun mbps(c: Context): Int = sp(c).getInt("mbps", 4)
    fun mic(c: Context): Boolean = sp(c).getBoolean("mic", false)

    fun save(c: Context, height: Int, fps: Int, mbps: Int, mic: Boolean) {
        sp(c).edit()
            .putInt("height", height)
            .putInt("fps", fps)
            .putInt("mbps", mbps)
            .putBoolean("mic", mic)
            .apply()
    }
}
