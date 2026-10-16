package com.h4era.screenrecorder

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var res by remember { mutableStateOf(Prefs.height(ctx)) }
    var fps by remember { mutableStateOf(Prefs.fps(ctx)) }
    var mbps by remember { mutableStateOf(Prefs.mbps(ctx)) }
    var mic by remember { mutableStateOf(Prefs.mic(ctx)) }

    fun commit() {
        Prefs.save(ctx, res, fps, mbps, mic)
    }

    val rowMod = Modifier.fillMaxWidth()
    val gap = Arrangement.spacedBy(8.dp)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.width(8.dp))
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Text("Quality preset", color = Cyan, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Row(rowMod, horizontalArrangement = gap) {
            Chip("Low", res == 480 && fps == 30 && mbps == 2, Modifier.weight(1f)) {
                res = 480; fps = 30; mbps = 2; commit()
            }
            Chip("Balanced", res == 720 && fps == 30 && mbps == 4, Modifier.weight(1f)) {
                res = 720; fps = 30; mbps = 4; commit()
            }
            Chip("High", res == 1080 && fps == 30 && mbps == 8, Modifier.weight(1f)) {
                res = 1080; fps = 30; mbps = 8; commit()
            }
            Chip("Max", res == 1080 && fps == 60 && mbps == 12, Modifier.weight(1f)) {
                res = 1080; fps = 60; mbps = 12; commit()
            }
        }

        Text("Resolution", color = Cyan, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Row(rowMod, horizontalArrangement = gap) {
            Chip("480p", res == 480, Modifier.weight(1f)) { res = 480; commit() }
            Chip("720p", res == 720, Modifier.weight(1f)) { res = 720; commit() }
            Chip("1080p", res == 1080, Modifier.weight(1f)) { res = 1080; commit() }
            Chip("Native", res == 0, Modifier.weight(1f)) { res = 0; commit() }
        }

        Text("Frame rate", color = Cyan, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Row(rowMod, horizontalArrangement = gap) {
            Chip("24", fps == 24, Modifier.weight(1f)) { fps = 24; commit() }
            Chip("30", fps == 30, Modifier.weight(1f)) { fps = 30; commit() }
            Chip("60", fps == 60, Modifier.weight(1f)) { fps = 60; commit() }
        }
        Text(
            "If your device can't do the chosen size or FPS, the app lowers it automatically.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )

        Text("Video bitrate (Mbps)", color = Cyan, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Row(rowMod, horizontalArrangement = gap) {
            Chip("2", mbps == 2, Modifier.weight(1f)) { mbps = 2; commit() }
            Chip("4", mbps == 4, Modifier.weight(1f)) { mbps = 4; commit() }
            Chip("8", mbps == 8, Modifier.weight(1f)) { mbps = 8; commit() }
            Chip("12", mbps == 12, Modifier.weight(1f)) { mbps = 12; commit() }
        }
        Text(
            "Estimated size: about " + String.format("%.0f", mbps * 60 / 8.0) + " MB per minute",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )

        Text("Audio", color = Cyan, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Row(rowMod, horizontalArrangement = gap) {
            Chip("No audio", !mic, Modifier.weight(1f)) { mic = false; commit() }
            Chip("Microphone", mic, Modifier.weight(1f)) { mic = true; commit() }
        }
        Text(
            "Internal (device) audio capture is coming in Phase 2.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )

        Text(
            "Changes apply to your next recording. Orientation follows your phone when you press REC.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp)
        )
    }
}
