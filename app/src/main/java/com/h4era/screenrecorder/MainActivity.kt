package com.h4era.screenrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private val projLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) {
            val i = Intent(this, RecordingService::class.java)
            i.putExtra("code", r.resultCode)
            i.putExtra("data", data)
            startForegroundService(i)
        } else {
            RecorderState.message = "Screen capture permission was denied."
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        askProjection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            H4Theme {
                App(
                    onToggle = { toggleRecord() },
                    onPause = { sendAction("TOGGLE") }
                )
            }
        }
    }

    private fun sendAction(a: String) {
        startService(Intent(this, RecordingService::class.java).setAction(a))
    }

    private fun toggleRecord() {
        if (RecorderState.recording) {
            sendAction("STOP")
            return
        }
        RecorderState.message = null
        val needed = ArrayList<String>()
        if (Prefs.mic(this) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) askProjection() else permLauncher.launch(needed.toTypedArray())
    }

    private fun askProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projLauncher.launch(mpm.createScreenCaptureIntent())
    }
}

@Composable
fun App(onToggle: () -> Unit, onPause: () -> Unit) {
    var screen by remember { mutableStateOf("home") }
    BackHandler(enabled = screen != "home") { screen = "home" }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0C1630), Navy)))
        ) {
            when (screen) {
                "library" -> LibraryScreen { screen = "home" }
                "settings" -> SettingsScreen { screen = "home" }
                else -> HomeScreen(
                    onToggle = onToggle,
                    onPause = onPause,
                    onLibrary = { screen = "library" },
                    onSettings = { screen = "settings" }
                )
            }
        }
    }
}
