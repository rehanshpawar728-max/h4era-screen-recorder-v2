package com.h4era.screenrecorder

import android.os.Environment
import android.text.format.Formatter
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onToggle: () -> Unit,
    onPause: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val rec = RecorderState.recording
    var free by remember { mutableStateOf(0L) }
    var recent by remember { mutableStateOf(listOf<Rec>()) }

    LaunchedEffect(RecorderState.libraryVersion, rec) {
        free = withContext(Dispatchers.IO) { Environment.getExternalStorageDirectory().usableSpace }
        recent = withContext(Dispatchers.IO) { MediaStoreManager.query(ctx).take(3) }
    }

    val h = Prefs.height(ctx)
    val settingsInfo = (if (h == 0) "Native" else "${h}p") +
            " • ${Prefs.fps(ctx)} FPS • ${Prefs.mbps(ctx)} Mbps"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("H4ERA", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Cyan)
        Text("SCREEN RECORDER", fontSize = 13.sp, color = Muted, letterSpacing = 4.sp)
        Spacer(Modifier.height(28.dp))

        Box(
            Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (rec) listOf(Color(0xFFFF5252), Color(0xFFB71C1C)) else listOf(Cyan, Blue)
                    )
                )
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (rec) "STOP" else "REC",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = if (rec) Color.White else Color.Black
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (rec && !RecorderState.paused) Color(0xFFFF5252)
                        else if (rec) Color(0xFFFFC107) else Color(0xFF4CAF50)
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (!rec) "Ready" else if (RecorderState.paused) "Paused" else "Recording",
                color = Color.White
            )
        }
        Text(
            fmtTime(RecorderState.elapsedSec),
            fontSize = 44.sp,
            fontWeight = FontWeight.Light,
            color = Color.White
        )
        if (rec) {
            OutlinedButton(onClick = onPause) {
                Text(if (RecorderState.paused) "Resume" else "Pause")
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Quality", color = Muted, fontSize = 12.sp)
                Text(if (rec) RecorderState.info else settingsInfo, color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text("Audio", color = Muted, fontSize = 12.sp)
                Text(if (Prefs.mic(ctx)) "Microphone" else "No audio", color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text("Free storage", color = Muted, fontSize = 12.sp)
                Text(Formatter.formatShortFileSize(ctx, free), color = Color.White)
            }
        }

        val msg = RecorderState.message
        if (msg != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2A3A))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(msg, color = Color.White, fontSize = 14.sp)
                    Row {
                        val saved = RecorderState.lastSaved
                        if (saved != null) {
                            TextButton(onClick = { playVideo(ctx, saved) }) { Text("Play") }
                            TextButton(onClick = { shareVideo(ctx, saved) }) { Text("Share") }
                            TextButton(onClick = onLibrary) { Text("Library") }
                        }
                        TextButton(onClick = { RecorderState.message = null }) { Text("Dismiss") }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onLibrary, modifier = Modifier.weight(1f)) { Text("Recordings") }
            Button(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Settings") }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Recent recordings",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        if (recent.isEmpty()) {
            Text("No recordings yet", color = Muted, modifier = Modifier.fillMaxWidth())
        }
        for (r in recent) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { playVideo(ctx, r.uri) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumb(r.uri, Modifier.size(width = 96.dp, height = 54.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        r.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        fmtTime(r.durationMs / 1000) + " • " + Formatter.formatShortFileSize(ctx, r.size),
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Privacy: recordings stay on your phone unless you share them. Nothing is uploaded.",
            color = Muted,
            fontSize = 11.sp
        )
    }
}
